# Unit 01 — SAGA Pattern (Cascading Failure Isolation)

## 가설

주문 생성은 본질적으로 다단계 (Order → Product 재고예약 → Payment 결제)
다. 이 흐름을 단일 트랜잭션으로 묶을 수 없으니 (서비스 경계가 곧
트랜잭션 경계) Order 가 Payment 까지 동기 RestClient 로 chain 하면
Payment 의 지연이 그대로 Order POST 의 latency 와 throughput 으로
새어 나간다 — *cascading failure*. SAGA + Kafka 는 Order 가 결제 결과
를 기다리지 않고 PENDING 으로 즉시 응답한 뒤 Payment 를 별도 reactor
로 비동기 처리하여 이 cascade 를 차단한다.

## 셋업

- Solution: `main` 빌드. `OrderSagaOrchestrator.startSaga` 가 stock
  reservation 까지만 동기, Payment 는 Kafka `order.created` 이벤트로
  발행 후 PENDING 반환.
- Problem: `evidence/01-no-saga` 빌드. 동일 흐름이지만 새로 추가한
  `PaymentSyncClient.process` 가 `POST /api/payments/process` 를 동기
  호출, 결과 (PAID / CANCELLED) 가 와야 응답. SagaInstance row 는 단일
  state-transition 으로 collapse.
- Cascading 트리거: `service-payment` 의 `PaymentStubProcessor` 에
  `app.chaos.payment-delay-ms` 환경변수로 2 초 인공 지연 주입. main
  코드에 추가된 무해 toggle (`APP_CHAOS_PAYMENT_DELAY_MS=2000`).
- 부하: `k6/scripts/cascading-failure.js` — 20 VUs constant_load 60s,
  variant 1 stock 100 k 로 stock 고갈 영향 제거.

## 결과

| 지표 | Solution (main, async SAGA) | Problem (no-saga, sync REST) |
|---|---|---|
| `http_req_duration` p95 | **3.03 s** | **5.26 s** |
| 성공 응답 p95 | 3.79 s | **6.37 s** |
| iterations / 60 s | **1 179** | 873 (-26 %) |
| VU low watermark | 20 (안정) | **1 (collapsed)** |
| `http_req_failed` rate | 81 % | 83 % |

raw output: [`solution/k6.txt`](./solution/k6.txt), [`problem/k6.txt`](./problem/k6.txt)

## 해석

- 두 빌드 모두 stock UPDATE row-lock 경합이 baseline latency 를 약 3 s
  까지 끌어올린다 (variant 1 단일 row 에 20 VUs 가 동시 접근).
- 이 baseline 위에 Payment 의 2 s 지연이 얹히면 Solution 은 Kafka
  publish 후 즉시 응답하므로 영향 없음 — `http_req_duration` p95 는
  3.03 s 그대로.
- Problem 은 PaymentSyncClient 가 그 2 s 를 동기로 기다리고 그 동안 VU
  가 점유되어 throughput 이 26 % 무너지고 VU 가 1 까지 saturate
  (`vus min=1`).
- 성공 응답만 보면 Solution 2.51 s avg → Problem 4.58 s avg. **결제
  지연이 그대로 주문 POST 에 누설** 되는 양이 약 2 s — 정확히 주입
  delay 의 양과 일치한다.

## 보조 evidence

- Tempo trace: Solution 의 POST `/api/orders` span tree 에는
  `service-payment` span 이 없고 Kafka producer span 만 보인다.
  Problem 은 `POST /api/payments/process` span 이 부모 trace 안에 자식으로 묶인다.
- Kafka UI (`http://34.64.219.137/kafka-ui/`) `order.created` 토픽:
  Solution 은 message rate 가 부하 그대로, Problem 은 sync 실패가
  많아 토픽 메시지 수가 적다.

## 재현

```bash
# Build no-saga service-order
docker buildx build --platform linux/amd64 --build-arg SERVICE_NAME=service-order \
  -t ecommerce/service-order:01-no-saga --load \
  ../ecommerce-microservices-worktrees/01-no-saga/backend-v2/

# Build payment image with chaos delay (main code, additive toggle)
docker buildx build --platform linux/amd64 --build-arg SERVICE_NAME=service-payment \
  -t ecommerce/service-payment:main-chaos --load backend-v2/

# Push + import + Solution leg
docker save ecommerce/service-payment:main-chaos > /tmp/p.tar
gcloud compute scp --zone=asia-northeast3-a /tmp/p.tar ecommerce-k3s:~/
gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a --command='
  sudo k3s ctr images import ~/p.tar; rm ~/p.tar
  sudo kubectl -n ecommerce set image deploy/service-payment service-payment=docker.io/ecommerce/service-payment:main-chaos
  sudo kubectl -n ecommerce set env deploy/service-payment APP_CHAOS_PAYMENT_DELAY_MS=2000'

# k6 Solution
gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a --command='
  PROM=$(sudo kubectl -n monitoring get svc prometheus -o jsonpath="{.spec.clusterIP}")
  ORDER_API=http://34.64.219.137 K6_PROMETHEUS_RW_SERVER_URL="http://${PROM}:9090/api/v1/write" \
  k6 run --tag testid=01-saga-solution-paymentslow --out experimental-prometheus-rw /tmp/k6-cascade.js'

# Swap in no-saga + k6 Problem
gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a --command='
  sudo kubectl -n ecommerce set image deploy/service-order service-order=docker.io/ecommerce/service-order:01-no-saga'
# … same k6 run, testid=01-saga-problem-paymentslow

# Restore main + clear chaos
gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a --command='
  sudo kubectl -n ecommerce set image deploy/service-order service-order=docker.io/ecommerce/service-order:latest
  sudo kubectl -n ecommerce set env deploy/service-payment APP_CHAOS_PAYMENT_DELAY_MS-'
```
