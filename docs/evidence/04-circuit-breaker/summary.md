# Unit 04 — Circuit Breaker

## 가설

타 서비스의 **응답 지연(slow call)** 시 동기 RestClient 호출 thread가
타임아웃까지 block 된다. 한정된 Tomcat thread pool이 빠르게 saturate
되어 Product를 거치지 않는 무관한 API까지 thread starvation으로 마비된다.
Resilience4j Circuit Breaker가 slow-call 비율을 모니터링하다 임계 초과
시 회로를 OPEN 하여 fast-fail 시키면 thread 점유를 차단할 수 있다.

## 셋업

- 스크립트: `k6/scripts/phase4-slow-product.js` — 30 VUs order-create + 5 VUs `/actuator/health`, 30s
- Chaos delay: `service-product`에 `APP_CHAOS_ENABLED=true`, `APP_CHAOS_STOCK_DELAY_MS=2000`
  (모든 `reserveStock` 호출이 server-side에서 2초 정지)
- 인프라: GCE e2-standard-4, k3s 단일 노드, public ingress 경유
- 모니터링: 기존 LGTM 스택 재사용. 대시보드 재배포 없음.

`evidence/04-no-cb` 워크트리는 `main`과 정확히 한 가지만 다르다 —
`ProductCatalogRestClient`의 `@CircuitBreaker` annotation 과 fallback
메서드가 제거되어 있고 `application.yml`의 resilience4j 블록도 빠짐.
Kafka, outbox, idempotency, JWT trust, 3-broker infra 모두 동일.

## 결과

| 지표 | Problem (no-cb) | Solution (main, CB on) |
|---|---|---|
| `order_create_duration` p95 | **15.00 s** (k6 timeout) | **1.64 s** |
| `order_create_duration` avg | 7.95 s | 575 ms |
| `order_create_duration` med | 12.39 s | 372 ms |
| `order_create` 체크 통과율 | 70 % (84/120) | 99 % (1338/1339) |
| `order_query_duration` p95 | 3.08 ms | 13.29 ms |
| iterations / 30s | **420** | **1 639** (3.9×) |
| VU low watermark | 1 (대부분 정체) | 35 (안정) |
| `http_req_duration` p95 (성공만) | 14.7 s | 1.04 s |
| Threshold `p(95)<3000` | **CROSSED** | passed |

raw k6 output: [`problem/k6.txt`](./problem/k6.txt), [`solution/k6.txt`](./solution/k6.txt)

## 해석

**Problem 측 (no-cb)** — order POST p95가 k6 iteration timeout (15s) 까지
밀려 있다. RestClient에 escape hatch가 없어 `reserveStock`가 2초 block
되면 그대로 thread를 점유한다. iteration 수가 Solution의 25 % 까지
떨어지고 (`vus min=1`) VU가 사실상 직렬화된다.

**Solution 측 (CB on)** — 5 슬로우콜이 sliding window를 채우면 CB가
OPEN 으로 전이, 후속 호출은 즉시 503 fast-fail. p95가 15s → 1.6s 로
거의 한 자리수 단축. iterations 도 4 배 회복.

`order_query_duration` 이 Solution 에서 *더 높게* 보이는 건 회귀가
아님 — Solution 에서는 order POST 가 503 으로 빠르게 회수되어 query
경로가 더 많은 traffic 과 thread 를 공유한다. Problem 에서는 order POST
가 thread 를 잡아두니 query 경로가 거의 idle 한 thread pool 위에서
돈다. 두 build 모두 query path 는 sub-15ms 로 안정. 핵심 신호는
**order_create p95 가 한 자리수 단축** 인 것.

## 재현

```bash
# Problem build
docker buildx build --platform linux/amd64 \
  --build-arg SERVICE_NAME=service-order \
  -t ecommerce/service-order:04-no-cb \
  --load ../ecommerce-microservices-worktrees/04-no-cb/backend-v2/

docker save ecommerce/service-order:04-no-cb | \
  gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a \
    --command='cat - > /tmp/order.tar && sudo k3s ctr images import /tmp/order.tar && rm /tmp/order.tar'

# Apply Problem
gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a --command='
  sudo kubectl -n ecommerce set image deploy/service-order \
    service-order=docker.io/ecommerce/service-order:04-no-cb
  sudo kubectl -n ecommerce rollout status deploy/service-order'

# k6 (run on the VM since Prometheus is ClusterIP-only)
gcloud compute scp --zone=asia-northeast3-a k6/scripts/phase4-slow-product.js ecommerce-k3s:/tmp/k6.js
gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a --command='
  PROM=$(sudo kubectl -n monitoring get svc prometheus -o jsonpath="{.spec.clusterIP}")
  ORDER_API=http://34.64.219.137 \
  K6_PROMETHEUS_RW_SERVER_URL="http://${PROM}:9090/api/v1/write" \
  k6 run --tag testid=04-cb-problem \
    --out experimental-prometheus-rw \
    /tmp/k6.js'

# Restore Solution
gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a --command='
  sudo kubectl -n ecommerce set image deploy/service-order \
    service-order=docker.io/ecommerce/service-order:latest
  sudo kubectl -n ecommerce rollout status deploy/service-order'
```
