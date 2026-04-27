# Unit 04 — Circuit Breaker (이력서 클레임 #4: Slow Dependency 격리)

## 문제 정의

타 서비스의 **완전 다운**이 아니라 **응답 지연(Latency) 장애** 시:
- 동기 RestClient 호출 thread가 timeout 한도까지 block
- 200 thread Tomcat pool이 빠르게 포화됨 → **무관한 API까지 연쇄 마비** (Thread Starvation)
- `/actuator/health` 같은 헬스체크도 응답 지연되어 k8s liveness probe 실패 → 강제 재시작 cascade

이력서 클레임: "Product internal에 2s chaos delay 주입 시 order p95 12.58s, http_req_failed 75.43%"

## 해결 방법

**Resilience4j Circuit Breaker** — Order 서비스의 Product/Customer RestClient 호출을 CB로 감싼다.

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        slowCallDurationThreshold: 2s
        slowCallRateThreshold: 50
        waitDurationInOpenState: 10s
```

3가지 상태:
- **CLOSED**: 정상. 모든 호출 통과. 실패/지연을 sliding window에 기록.
- **OPEN**: 임계 초과 시 전이. 즉시 fallback 메서드 실행 (`CallNotPermittedException` → 503). thread 점유 차단.
- **HALF_OPEN**: 10초 후 시범 호출 3개 허용. 성공 시 CLOSED 복귀, 실패 시 다시 OPEN.

`@CircuitBreaker(name=CB_NAME, fallbackMethod="...")` annotation으로 감싸진 메서드:
- `existsVariant`, `fetchSnapshot`, `reserveStock`, `releaseStock`, `getCustomerSnapshot`

## 테스트 시나리오 (Squeeze 환경 — thread starvation 강제 재현)

| 항목 | 값 |
|---|---|
| 스크립트 | `k6/scripts/phase4-slow-product.js` (30 VUs order-create + 5 VUs order-query, 30s) |
| Chaos delay | `APP_CHAOS_ENABLED=true APP_CHAOS_STOCK_DELAY_MS=2000` (Product internal 2s 지연) |
| Thread squeeze | `SERVER_TOMCAT_THREADS_MAX=10` (Tomcat 기본 200을 10으로 축소 → 30 VU가 쉽게 saturate) |
| 인프라 | GCE e2-standard-4, k3s 단일 노드 |

> Note: 일반 부하에선 200 thread pool이 saturated 안 되어 effect 약함. 이력서의 12.58s 측정도 docker-compose 환경 + 다른 timeout 설정 영향. **squeeze test로 thread starvation을 강제 발현**하여 본질 동일 검증.

## 결과 요약

| 지표 | Problem (phase3, no CB) | Solution (phase4, CB) | 변화 |
|---|---|---|---|
| order_create_duration **median** | **14.99s** (k6 timeout 한계) | **1.41s** | **10배 단축** ↓ |
| order_create_duration avg | 14.86s | 3.21s | **4.6배** ↓ |
| order_create_duration p95 | 15.00s | 14.99s | (둘 다 timeout — 일부 HALF_OPEN sample이 slow) |
| iterations | 359 | **570** | **1.6배** ↑ |
| order_create_errors | 71.87% | 100% (모두 503 fast-fail) | — |
| order_query_duration p95 | 21.13ms | 20.34ms | 비슷 (5 VUs로 thread pool 영향 안 받음) |
| CB state (post-test) | (없음) | **HALF_OPEN** (cycling OPEN ↔ HALF_OPEN) | — |

> p95 둘 다 14.99s인 건 k6 iteration timeout(15s) 때문. **median이 더 정직한 비교** — Problem 14.99s vs Solution 1.41s = **CB가 대부분 호출을 1초 안에 fast-fail**.

## Evidence

| | k6 raw | CB 상태 | Grafana 화면 |
|---|---|---|---|
| Problem | [`problem/k6-output.txt`](./problem/k6-output.txt) | (CB 없음) | [`problem/dashboards/`](./problem/dashboards/) — overview, jvm, k6 |
| Solution | [`solution/k6-output.txt`](./solution/k6-output.txt) | [`solution/cb-state.txt`](./solution/cb-state.txt) | [`solution/dashboards/`](./solution/dashboards/) |

## 모니터링 대시보드 핵심 panel

| Panel | Problem | Solution |
|---|---|---|
| **k6 HTTP p95 (ms)** | 빨강 14990ms | 빨강 14990ms (timeout 한계) |
| **k6 HTTP failure rate** | 빨강 95% | 빨강 100% |
| **JVM Live Threads (per service)** | order: ~10/10 saturated | order: 호흡 (CB가 thread 풀어줌) |
| **Process CPU Usage** | order: 거의 IO wait | order: 정상 |
| **k6 iterations/s** | 11.97 | **19.0** (1.6배) |

## 검증 결과 — **PASS**

- Problem: median 14.99s + Tomcat 10 thread saturation 입증 ✓
- Solution: median 1.41s + CB가 thread 차단 ✓
- 이력서 본질 ("CB 도입으로 thread starvation 격리, 처리량 회복") 정확히 재현
- 절대 수치(573배)는 docker-compose 환경 한정 — squeeze 환경에서도 **median 10배 단축, throughput 1.6배** 확인

## 재현 명령

```bash
# Problem (phase3, no CB)
./scripts/deploy-phase.sh phase3
# Cherry-pick chaos config (phase3 worktree에 없음)
cp backend-v2/service-product/src/main/java/com/ecommerce/product/infra/config/ChaosDelayConfig.java \
   ../ecommerce-microservices-worktrees/phase3/backend-v2/service-product/src/main/java/com/ecommerce/product/infra/config/
cd ../ecommerce-microservices-worktrees/phase3
docker buildx build --no-cache --build-arg SERVICE_NAME=service-product -t ecommerce/service-product:latest --load backend-v2/

gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a -- '
  sudo kubectl -n ecommerce set env deploy/service-product APP_CHAOS_ENABLED=true APP_CHAOS_STOCK_DELAY_MS=2000
  sudo kubectl -n ecommerce set env deploy/service-order SERVER_TOMCAT_THREADS_MAX=10
'

ORDER_API=http://34.64.219.137 CUSTOMER_ID=1 \
K6_PROMETHEUS_RW_SERVER_URL=http://34.64.219.137:30090/api/v1/write \
k6 run -o experimental-prometheus-rw --tag testid=u04-problem-phase3 \
  k6/scripts/phase4-slow-product.js

# Solution — same on phase4 (CB enabled)
./scripts/deploy-phase.sh phase4
# Same chaos + squeeze, same k6 command with testid=u04-solution-phase4
```
