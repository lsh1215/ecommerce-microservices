# Unit 02 — Transactional Outbox

## 가설

도메인 트랜잭션 (예: 주문 생성) 과 이벤트 발행 (Kafka send) 은 한
트랜잭션으로 묶을 수 없다. 둘 사이에 broker 장애나 클라이언트 크래시
가 끼면 DB 와 Kafka 가 영구적으로 어긋난다 — `order_created` 가 DB
에 박혔는데 downstream 은 그 사실을 끝까지 모르는 식. Transactional
Outbox 는 이벤트를 `outbox_event` 테이블에 같은 트랜잭션으로 INSERT
한 뒤 별도 poller 가 outbox 를 보고 Kafka 에 발행, 발행 성공 시 row
상태를 PUBLISHED 로 바꾼다. broker 가 잠시 죽어도 outbox 가 retry 의
source-of-truth 역할을 한다.

## 셋업

- Solution: `main` 빌드. `OrderOutboxEventHandler.@TransactionalEventListener(BEFORE_COMMIT)` 가 outbox row INSERT, `OutboxPollingPublisher@Scheduled(fixedDelay=500ms)` 가 PENDING row 를 읽어 Kafka 발행 후 PUBLISHED 로 마킹.
- Problem: `evidence/02-no-outbox` 빌드. handler 가 `AFTER_COMMIT` + `KafkaTemplate.send` 로 바뀌어 outbox 테이블을 우회.
- 시나리오: 정상 주문 → broker 다운 + 발행자 producer 버퍼 드롭 (`scale kafka 0` + `delete pod service-order`) 후 주문 → broker 복구 후 두 주문의 결과 비교.

## 결과

| 시나리오 | Solution (outbox) | Problem (no-outbox) |
|---|---|---|
| 정상 주문 | DB Order PAID + outbox PUBLISHED + Payment COMPLETED | DB Order PAID + Payment COMPLETED (broker 정상이면 동작) |
| broker 다운 + producer buffer drop 한 주문 | (해당 시나리오에서 outbox 가 PENDING 으로 남아 broker 복구 시 자동 retry → 최종 일관성) | **Order PENDING, Payment 0건, outbox 새 row 0건 — 영구 유실** |

raw output: [`solution/state.txt`](./solution/state.txt), [`problem/state.txt`](./problem/state.txt) · Kafka UI topic JSON: [`solution/kafka-ui-order-created.json`](./solution/kafka-ui-order-created.json)

세부 수치 (이번 캡처):

- Solution outbox 테이블 — id ≤ 89 모두 `PUBLISHED`, retry_count 모두 0, status COUNT: PUBLISHED=89.
- Problem 빌드에서 broker 정상 시 발행한 주문 (id 91): Order CANCELLED + Payment FAILED — broker 복구 직후 producer accumulator 가 retry → consumer 가 받음. stub 의 10 % 실패 확률에 걸려 FAILED. **producer 의 in-memory retry 가 우연히 성공한 케이스 — outbox 의 보장과는 본질적으로 다름.**
- Problem 빌드에서 broker 다운 + 발행자 pod 강제 삭제한 주문 (id 92): Order **PENDING**, Payment **0건**, outbox **0 새 row**. broker 가 돌아왔지만 in-memory 버퍼가 사라져 발행 자체가 영구 실패. DB 만 변경되고 Kafka 는 그 사실을 끝까지 모름.

## 해석

- Outbox 는 "도메인 트랜잭션과 이벤트 발행을 atomically 묶는다" 는 단순 명제. 실제 효과는 broker 장애 + 발행자 크래시 같은 *겹친* 장애에서 비로소 가시화 — `Problem(id 91)` 처럼 producer 의 retry 만으로 우연히 성공하는 케이스도 있어 단순 broker 다운 만으로는 evidence 가 약하다.
- Solution 측의 outbox 테이블은 *dual-write* 문제를 명시적 schema 로 옮긴 것. PUBLISHED row 의 누적이 그 자체로 audit log.
- Kafka UI 에서 두 build 의 차이는 *topic 에 메시지가 도달했는지* 로 보면 명확. Problem 의 잃어버린 주문 (id 92) 의 `orderNumber` 는 `order.created` 토픽 검색에 나오지 않음.

## Kafka UI

`http://34.64.219.137/kafka-ui/` 에서 `ecommerce` 클러스터 → `order.created` 토픽을 열어 `Messages` 탭에서 `orderNumber` 로 검색해 비교한다.

## 재현

```bash
# Build no-outbox images
docker buildx build --platform linux/amd64 --build-arg SERVICE_NAME=service-order \
  -t ecommerce/service-order:02-no-outbox --load \
  ../ecommerce-microservices-worktrees/02-no-outbox/backend-v2/
docker buildx build --platform linux/amd64 --build-arg SERVICE_NAME=service-payment \
  -t ecommerce/service-payment:02-no-outbox --load \
  ../ecommerce-microservices-worktrees/02-no-outbox/backend-v2/

# scp + import + swap
docker save ecommerce/service-order:02-no-outbox > /tmp/o.tar
docker save ecommerce/service-payment:02-no-outbox > /tmp/p.tar
gcloud compute scp --zone=asia-northeast3-a /tmp/o.tar /tmp/p.tar ecommerce-k3s:~/
gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a --command='
  sudo k3s ctr images import ~/o.tar
  sudo k3s ctr images import ~/p.tar
  sudo kubectl -n ecommerce set image deploy/service-order service-order=docker.io/ecommerce/service-order:02-no-outbox
  sudo kubectl -n ecommerce set image deploy/service-payment service-payment=docker.io/ecommerce/service-payment:02-no-outbox'

# Chaos: kafka down + drop producer buffer + submit
gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a --command='
  sudo kubectl -n ecommerce scale deploy/kafka --replicas=0; sleep 8'
curl -X POST -H 'Authorization: Bearer ...' -d '{"items":[{...}],...}' http://34.64.219.137/api/orders
gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a --command='
  sudo kubectl -n ecommerce delete pods -l app=service-order --grace-period=1
  sudo kubectl -n ecommerce scale deploy/kafka --replicas=1'

# Verify orphan: order PENDING, no payment, no outbox row
# Restore main
gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a --command='
  sudo kubectl -n ecommerce set image deploy/service-order service-order=docker.io/ecommerce/service-order:latest
  sudo kubectl -n ecommerce set image deploy/service-payment service-payment=docker.io/ecommerce/service-payment:latest'
```
