# Unit 01 — Cascading Failure (이력서 클레임 #2: SAGA Orchestration)

## 문제 정의

이커머스 4-service 마이크로서비스 환경에서 Order 서비스가 Payment를 **동기 RestClient**로 호출하는 구조였다. Payment 단일 장애가 발생하면:

- Order의 RestClient 호출이 `Connection refused`로 즉시 실패
- 200 thread Tomcat pool이 retry/timeout 대기로 점유됨
- **주문 매출이 즉시 100% 중단됨** (가용성 곱셈 결합)

## 해결 방법

Order ↔ Payment 경로를 **Kafka 이벤트 비동기**로 전환 + **SAGA Orchestration**:

```
Before: k6 → Order ─sync RestClient→ Payment(DOWN) ❌ 5xx
After:  k6 → Order ─kafka publish→ order.created (queued)
                  └─→ 201 PENDING (즉시 반환)
        Payment 복구 후 consumer가 큐에서 소비
        → payment.completed 이벤트 → SAGA Orchestrator
        → Order PENDING → PAID 상태 전이
```

핵심 코드:
- `OrderEventProducer.publishOrderCreated()` — `kafkaTemplate.send(...).whenComplete(...)` fire-and-forget
- `OrderSagaOrchestrator` — SAGA 상태 머신 (`startSaga`, `onPaymentCompleted`, `onPaymentFailed`, `compensate`)

## 테스트 시나리오 (problem / solution 동일)

| 항목 | 값 |
|---|---|
| 스크립트 | `k6/scripts/order-flow.js` (3 reqs/iter: products GET, product detail GET, order POST) |
| 부하 | `--vus 5 --duration 30s` |
| 장애 주입 | `kubectl scale deploy/service-payment --replicas=0` |
| 인프라 | GCE e2-standard-4, k3s 단일 노드 |
| 모니터링 | Grafana LGTM stack (재배포 없이 재사용) |

## 결과 요약

| 지표 | Problem (phase0, sync) | Solution (phase1, async SAGA) | 변화 |
|---|---|---|---|
| 총 요청 | 51 | 414 | 8.1배 ↑ |
| `http_req_failed` | **33.33%** (17/51) | **0.00%** (0/414) | -33.33%p |
| 주문 생성 (201) | **0건** | **138건** | — |
| `iteration_duration` p95 | 21.96s | **1.28s** | 17배 단축 |
| 처리량 | 0.48 iter/s | **4.45 iter/s** | 9.2배 ↑ |
| 응답 본문 | `Connection refused` 5xx | 201 `status=PENDING` | — |

> 33%는 3 reqs/iter 중 order POST 1건만 실패 → **주문 가용성 관점 100% → 0% 실패**.

## Evidence

| | k6 raw output | Grafana 화면 |
|---|---|---|
| Problem | [`problem/k6-output.txt`](./problem/k6-output.txt) | [`problem/dashboards/`](./problem/dashboards/) — ecommerce-overview, k6-prometheus |
| Solution | [`solution/k6-output.txt`](./solution/k6-output.txt) | [`solution/dashboards/`](./solution/dashboards/) — ecommerce-overview, k6-prometheus |

## 모니터링 대시보드 핵심 panel

같은 `Ecommerce Overview` 대시보드에서 두 시점을 비교:

| Panel | Problem | Solution |
|---|---|---|
| **k6 HTTP failure rate** | 빨강 33.3% | 초록 0% |
| **HTTP Request Rate (per service, via OTLP)** | order에서 5xx 폭발 | order 정상 2xx |
| **Service Graph (Tempo)** | order→payment 빨간 엣지 | order→kafka 정상 |
| **JVM UP** | 노랑 3 (payment 다운) | 노랑 3 (동일 — 인프라 변동 없음, 비즈니스 결과만 다름) |

## 검증 결과 — **PASS**

기대값 대비 실측 일치:
- Problem: order POST 100% 실패 ✓ (예상치 일치)
- Solution: order POST 100% 성공 ✓ (예상치 일치)
- 처리량 9배 향상 — Tomcat thread 차단 제거 효과

## 재현 명령

```bash
# Problem
./scripts/deploy-phase.sh phase0
gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a -- \
  'sudo kubectl -n ecommerce scale deploy/service-payment --replicas=0'
PRODUCT_API=http://34.64.219.137 ORDER_API=http://34.64.219.137 \
K6_PROMETHEUS_RW_SERVER_URL=http://34.64.219.137:30090/api/v1/write \
k6 run --vus 5 --duration 30s -o experimental-prometheus-rw \
  --tag testid=u01-problem-phase0 \
  k6/scripts/order-flow.js

# Solution — same command after `./scripts/deploy-phase.sh phase1`
```
