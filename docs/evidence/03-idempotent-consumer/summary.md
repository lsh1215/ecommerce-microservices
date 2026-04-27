# Unit 03 — Idempotent Consumer

## 가설

Kafka delivery 보장은 at-least-once 다. 동일 메시지가 (rebalance, retry,
broker hiccup 등으로) 두 번 이상 도착할 수 있다. 핸들러에 멱등성
가드 (eventId 기반 dedup + business-state 검사) 가 없으면 동일 결제가
중복 생성된다.

## 셋업

`order.created` 토픽에 동일 페이로드 (`eventId` 동일, `orderId` 동일)
를 5번 inject 하고 service-payment 의 결과 row 수를 센다. dedup 가드를
toggle off 시키는 두 환경 변수 (`APPLICATION_IDEMPOTENCY_ENABLED`,
`APPLICATION_BUSINESS_IDEMPOTENCY_GUARD_ENABLED`) 가 코드에 wiring 되어
있어 (`@Value("${application.idempotency.enabled:true}")`) Problem 측
은 env override 만으로 재현 가능. evidence/03-no-idempotency 워크트리는
같은 toggle 을 빌드 타임에 yml 로 굽는다.

이번 캡처는 evidence/03-no-idempotency 빌드의 결과를 확인하기 위해
service-payment 만 main 코드로 fresh 빌드 후 env override 로 toggle
했다 — 워크트리 빌드와 동일한 동작. 다른 서비스 + Kafka + DB 모두
공유.

## 결과

| 지표 | Solution (idempotency on) | Problem (toggles off) |
|---|---|---|
| 동일 eventId 메시지 | 5건 inject | 5건 inject |
| `payment` 테이블 신규 row | **1** | **5** |
| `processed_event` 테이블 row | 1 (eventId 단일) | 0 (dedup 미사용) |
| 4× dedup INFO log | "중복 이벤트 감지, 건너뜀" | — |
| WARN log | — | "idempotency disabled" + "guard disabled" 5쌍 |

5건 중 1건이 FAILED 인 건 `PaymentStubProcessor` 의 90 % 성공률 시뮬레이션 때문. dedup 동작 자체와는 무관.

raw output: [`solution/raw.txt`](./solution/raw.txt), [`problem/raw.txt`](./problem/raw.txt)

## 해석

Solution 측 — IdempotentEventHandler 의 첫 호출이 `processed_event(eventId)`
row 를 INSERT (UNIQUE constraint), 후속 4 건은 `existsByEventId` 가 true
를 반환하여 processor 를 건너뜀. PaymentService 는 1번만 호출되어
payment row 1 개. INSERT 단계에서 race 가 나도 DB UNIQUE 가 최후 가드
역할.

Problem 측 — 두 toggle 모두 false. IdempotentEventHandler 는 dedup 검사
없이 processor 를 매번 실행. PaymentService.processFromEvent 의 business
guard (이미 COMPLETED Payment 가 있으면 skip) 도 비활성. 결과적으로 5건
의 payment row 가 동일 orderId 에 대해 생성됨. transaction_id 5건이 모두
다른 게 그 증거.

## 재현

```bash
# Solution: idempotency on (default)
EVENT_ID=test-evt-03-sol PAYLOAD='{...same eventId...}'
for i in 1 2 3 4 5; do
  echo "$PAYLOAD" | kubectl exec -i kafka-... -- kafka-console-producer ...
done
kubectl exec mysql-0 -- mysql ... "SELECT COUNT(*) FROM payment WHERE order_id=99001"

# Problem: toggles off
gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a --command='
  sudo kubectl -n ecommerce set env deploy/service-payment \
    APPLICATION_IDEMPOTENCY_ENABLED=false \
    APPLICATION_BUSINESS_IDEMPOTENCY_GUARD_ENABLED=false
  sudo kubectl -n ecommerce rollout status deploy/service-payment'

# repeat with new orderId; expect 5 payment rows

# Restore
gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a --command='
  sudo kubectl -n ecommerce set env deploy/service-payment \
    APPLICATION_IDEMPOTENCY_ENABLED- APPLICATION_BUSINESS_IDEMPOTENCY_GUARD_ENABLED-'
```
