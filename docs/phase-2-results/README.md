# Phase 2 — Transactional Outbox (이벤트 유실 방지)

- **Worktree**: `/Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase2` (`eedeaa3`)
- **Evidence**:
  - [`evidence/before-kafka-down.txt`](./evidence/before-kafka-down.txt) (Phase 1 worktree 에서 재현)
  - [`evidence/after-kafka-down.txt`](./evidence/after-kafka-down.txt) (Phase 2 worktree)

## 문제 정의 (Problem)

[Phase 1](../phase-1-results/README.md) 에서 Order → Payment 를 Kafka 이벤트로 비동기화했지만, 이벤트 발행 코드는 여전히 **DB 트랜잭션과 분리**되어 있다.

```java
// Phase 1: backend-v2/service-order/.../OrderSagaOrchestrator.java (22b8d1f)
@Transactional
public Order startSaga(CreateOrderCommand cmd) {
    Order order = orderRepository.save(newOrder);                    // (1) DB commit 대상
    eventProducer.publishOrderCreated(new OrderCreatedEvent(...));   // (2) kafkaTemplate.send().whenComplete()
    return order;
}
```

**Dual Write** 문제:
- `(1)` 은 DB 트랜잭션에 포함. 커밋 성공이 보장됨.
- `(2)` 는 DB 트랜잭션 밖. Kafka 다운이면 send() Future 가 비동기로 실패 — 프로세스 OOM/evict 시 producer buffer 와 함께 **이벤트 영구 유실**.

실측 증거: [`evidence/before-kafka-down.txt`](./evidence/before-kafka-down.txt) — Kafka 다운 + Order 프로세스 kill 후 재시작 시 3 개 주문 중 0 개 Payment 생성 (모두 유실).

## 해결 방법 (Solution)

**Transactional Outbox 패턴.** 이벤트를 **Kafka 대신 DB 의 `outbox_event` 테이블에 먼저 INSERT** 한다. 비즈니스 엔티티 저장과 같은 트랜잭션이므로 원자성 보장. 별도 poller 가 `status=PENDING` 행을 주기적으로 Kafka 에 발행하고 `status=PUBLISHED` 로 전이.

| 단계 | 메커니즘 |
|---|---|
| **이벤트 적재** | `@TransactionalEventListener(phase = BEFORE_COMMIT)` 로 `outbox_event` INSERT. 비즈니스 커밋이 실패하면 이벤트도 롤백. |
| **이벤트 발행** | `OutboxPollingPublisher` (`@Scheduled(fixedDelay=500ms)`) 가 `findTop100ByStatusOrderByCreatedAtAsc(PENDING)` 조회 후 `kafkaTemplate.send().get(5s)` 동기 발행 |
| **성공** | `markPublished()` → `status=PUBLISHED` + `publishedAt=NOW()` |
| **실패** | `retry_count++` + `break` (헤드 블로킹 방지). 5회 초과 → `markFailed()` → `status=FAILED`, 다음 이벤트로 continue. |
| **경쟁 제어** | `@Version` (낙관적 락) + `processed_event.event_id` UNIQUE (Phase 3 과 결합) |

### 스키마

```sql
CREATE TABLE outbox_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(26) NOT NULL UNIQUE,  -- ULID
  aggregate_type VARCHAR(50) NOT NULL,
  aggregate_id VARCHAR(50) NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  payload TEXT NOT NULL,
  partition_key VARCHAR(100) NOT NULL,
  status ENUM('PENDING','PUBLISHED','FAILED') NOT NULL DEFAULT 'PENDING',
  published_at DATETIME(6) NULL,
  retry_count INT NOT NULL DEFAULT 0,
  version BIGINT,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  INDEX idx_outbox_status_created (status, created_at)
);
```

복합 인덱스 `(status, created_at)` 이 `WHERE status='PENDING' ORDER BY created_at ASC LIMIT 100` 쿼리를 커버한다.

### 구현 포인트

| 파일 | 역할 |
|---|---|
| `backend-v2/common/src/main/java/com/ecommerce/common/outbox/OutboxEvent.java` | Entity. `status` enum + `@Version` + `markPublished()` / `markFailed()` |
| `backend-v2/common/src/main/java/com/ecommerce/common/outbox/OutboxEventStatus.java` | `PENDING / PUBLISHED / FAILED` |
| `backend-v2/common/src/main/java/com/ecommerce/common/outbox/OutboxEventRepository.java` | `findTop100ByStatusOrderByCreatedAtAsc(status)` |
| `backend-v2/common/src/main/java/com/ecommerce/common/outbox/OutboxPollingPublisher.java` | `@Scheduled(fixedDelay=500)` poller |
| `backend-v2/service-order/.../OrderOutboxEventHandler.java` | Order 의 도메인 이벤트를 Outbox 로 적재하는 `@TransactionalEventListener` |

## Before / After 핵심 수치

| 시나리오 | Phase 1 (Before) | Phase 2 (After) |
|---|---|---|
| Kafka stop 중 주문 POST 3회 | 전부 `201 PENDING` 저장 (orders 테이블 OK) | 동일하게 `201 PENDING` |
| Kafka stop 중 `outbox_event` 상태 | 테이블 없음 (Phase 1 에 없음) | 3 rows `status=PENDING` |
| Order 프로세스 kill 후 Kafka 재기동 + 30초 | 0 payments for 3 test orders (producer buffer 유실) | **3 payments COMPLETED** (outbox poller 가 재발행) |
| `outbox_event` 최종 상태 | n/a | **3 rows `status=PUBLISHED`, `retry_count=0`** |

수치 근거: [`evidence/before-kafka-down.txt`](./evidence/before-kafka-down.txt), [`evidence/after-kafka-down.txt`](./evidence/after-kafka-down.txt).

---

## 🧪 Testing Guide

### 1. 테스트 종류

| 항목 | 내용 |
|---|---|
| **유형** | Chaos Engineering + State-transition 검증 (DB snapshot 기반) |
| **부하 생성기** | 없음. 수동 curl 3회 POST + DB 쿼리 관찰 (이벤트 유실 여부는 부하와 무관, **재현성 + 증거 명확성**이 최우선) |
| **장애 주입** | `docker stop ecommerce-kafka` + Order 서비스 `kill -9` (프로세스 eviction 시뮬레이션) |
| **가설 (Before)** | "Phase 1 의 fire-and-forget Kafka send 는 producer buffer 소실 시 이벤트 유실" |
| **가설 (After)** | "Outbox 는 producer buffer 와 무관하게 DB 에 이벤트를 보존하므로 재시작 후에도 Kafka 재발행이 가능" |

### 2. 실행 방법

#### Step A. Before 재현 (Phase 1 worktree — 이벤트 유실)

```bash
cd /Users/leesanghun/My_Project/ecommerce-microservices
(cd /Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase1/backend-v2 \
  && ./gradlew bootJar -x test -q)

docker exec ecommerce-mysql mysql -uroot -p1234 -e "
  DROP DATABASE IF EXISTS ecommerce_order;   CREATE DATABASE ecommerce_order;
  DROP DATABASE IF EXISTS ecommerce_product; CREATE DATABASE ecommerce_product;
  DROP DATABASE IF EXISTS ecommerce_customer; CREATE DATABASE ecommerce_customer;
  DROP DATABASE IF EXISTS ecommerce_payment; CREATE DATABASE ecommerce_payment;"

# phase1 전체 4개 서비스 기동 (Pinpoint agent 부착)
./scripts/run-worktree-with-pinpoint.sh phase1

docker exec -i ecommerce-mysql mysql -uroot -p1234 < scripts/seed-data.sql
docker exec ecommerce-mysql mysql -uroot -p1234 -e \
  "USE ecommerce_product; UPDATE product_variant SET stock_quantity = 100000 WHERE id IN (1,2,3,4,5);"

# Kafka 중지 → 주문 3건 POST → Order kill → Kafka 재기동 → 30초 대기 → 스냅샷
MAIN_DOCS=/Users/leesanghun/My_Project/ecommerce-microservices/docs
{
  echo "=== T0: snapshot ==="
  docker exec ecommerce-mysql mysql -uroot -p1234 -e "
    SELECT 'orders', COUNT(*) FROM ecommerce_order.orders
    UNION ALL SELECT 'payment', COUNT(*) FROM ecommerce_payment.payment;"

  echo "=== T1: docker stop kafka ==="
  docker stop ecommerce-kafka

  echo "=== T2: POST 3 orders with Kafka down ==="
  for i in 1 2 3; do
    curl -sS -X POST http://localhost:8082/api/orders \
      -H 'Content-Type: application/json' \
      -d '{"customerId":1,"items":[{"productVariantId":2,"productId":1,"productName":"LostTest","size":"M","color":"White","unitPrice":29900,"quantity":1}],"shippingAddress":{"recipientName":"T","phone":"010-0000-0000","zipCode":"06234","address1":"Seoul","address2":"X"}}' \
      | head -c 200; echo
  done

  echo "=== T3: kill Order to flush producer buffer ==="
  pid=$(lsof -iTCP:8082 -sTCP:LISTEN -P -n | awk 'NR>1 {print $2}' | head -1)
  [ -n "$pid" ] && kill -9 $pid

  echo "=== T4: start Kafka + restart Order ==="
  docker start ecommerce-kafka; sleep 10
  ./scripts/run-worktree-with-pinpoint.sh phase1 order; sleep 20

  echo "=== T5: final snapshot — payment should be 0 for test orders ==="
  docker exec ecommerce-mysql mysql -uroot -p1234 -e "
    SELECT id, order_number, status FROM ecommerce_order.orders;
    SELECT COUNT(*) AS payments FROM ecommerce_payment.payment;"
} | tee $MAIN_DOCS/phase-2-results/evidence/before-kafka-down.txt
```

#### Step B. After 증명 (Phase 2 worktree — Outbox 복구)

```bash
# phase1 서비스 정지
for p in 8081 8082 8083 8084; do pid=$(lsof -iTCP:$p -sTCP:LISTEN | awk 'NR>1 {print $2}'); [ -n "$pid" ] && kill $pid; done
sleep 5

(cd /Users/leesanghun/My_Project/ecommerce-microservices-worktrees/phase2/backend-v2 \
  && ./gradlew bootJar -x test -q)

docker exec ecommerce-mysql mysql -uroot -p1234 -e "
  DROP DATABASE IF EXISTS ecommerce_order;   CREATE DATABASE ecommerce_order;
  DROP DATABASE IF EXISTS ecommerce_product; CREATE DATABASE ecommerce_product;
  DROP DATABASE IF EXISTS ecommerce_customer; CREATE DATABASE ecommerce_customer;
  DROP DATABASE IF EXISTS ecommerce_payment; CREATE DATABASE ecommerce_payment;"

./scripts/run-worktree-with-pinpoint.sh phase2

docker exec -i ecommerce-mysql mysql -uroot -p1234 < scripts/seed-data.sql
docker exec ecommerce-mysql mysql -uroot -p1234 -e \
  "USE ecommerce_product; UPDATE product_variant SET stock_quantity = 100000 WHERE id IN (1,2,3,4,5);"

# Phase2 consumer 가 historical Kafka event 를 다 소화할 시간을 준 뒤 테스트 시작
sleep 15

MAIN_DOCS=/Users/leesanghun/My_Project/ecommerce-microservices/docs
{
  # ... 위 Before 와 동일한 T0-T5 스냅샷
  # 단, 각 스냅샷에 outbox_event 테이블도 추가
  echo "=== T3.5: outbox_event 스냅샷 ==="
  docker exec ecommerce-mysql mysql -uroot -p1234 -e "
    SELECT event_id, event_type, status, retry_count
    FROM ecommerce_order.outbox_event;"
} | tee $MAIN_DOCS/phase-2-results/evidence/after-kafka-down.txt
```

전체 흐름은 [`scripts/phase3-multi-consumer-test.sh`](../../scripts/phase3-multi-consumer-test.sh) 와 유사하게 한 스크립트로 자동화해도 된다. 이미 커밋된 [`evidence/after-kafka-down.txt`](./evidence/after-kafka-down.txt) 가 참조 모델.

### 3. 확인 지표

| 지표 | 출처 | Before (Phase 1) | After (Phase 2) |
|---|---|---|---|
| `ecommerce_order.orders` count (3 테스트 주문 후) | MySQL | 3 | 3 |
| `ecommerce_order.outbox_event` | MySQL | (테이블 없음) | 3 rows `status=PENDING` → **`PUBLISHED`** |
| `ecommerce_payment.payment` (테스트 주문용) | MySQL | **0 (유실)** | 3 COMPLETED |
| `outbox_event.retry_count` | MySQL | n/a | 0 또는 소수 (Kafka 재기동 직후) |
| Pinpoint Server Map (`service-order-phase2`) | `:8079` | n/a | **OutboxPollingPublisher → Kafka 노드** 가 별도 trace 로 표시됨 |

### 4. 포트폴리오 증거 캡처

#### 🥇 대표 이미지: DB 쿼리 결과 — Outbox 상태 전이

DBeaver / DataGrip / MySQL Workbench 등 GUI 로 아래 쿼리를 실행해 **두 시점의 결과를 한 장에 합성**.

```sql
-- Kafka 다운 직후 (status=PENDING)
SELECT event_id, event_type, status, retry_count, created_at
FROM ecommerce_order.outbox_event
ORDER BY created_at DESC
LIMIT 3;

-- Kafka 재기동 30초 후 (status=PUBLISHED)
SELECT event_id, event_type, status, retry_count, published_at
FROM ecommerce_order.outbox_event
ORDER BY created_at DESC
LIMIT 3;
```

저장: `docs/phase-2-results/evidence/outbox-state-transition.png`

> 💡 캡션: _"Kafka 다운 중에도 이벤트가 outbox_event 테이블에 `PENDING` 으로 보존되고, Kafka 복구 30초 뒤 OutboxPollingPublisher 가 자동으로 `PUBLISHED` 로 전이시켰다."_

#### 🥈 보조 이미지 1: Pinpoint Call Tree — Outbox 분리 구조

Phase 2 에서 `POST /api/orders` 의 Pinpoint Call Tree 를 열면:
1. Order 서비스 트랜잭션에 **`outboxEventRepository.save()`** 노드가 포함됨
2. 별도 `OutboxPollingPublisher.publishPendingEvents()` 트랜잭션이 주기적으로 뜸 (`@Scheduled`)

두 트랜잭션을 각각 캡처해 **"같은 TX 안에서 비즈니스 + Outbox 저장, 별도 스케줄러가 발행"** 구조를 시각화.

저장: `docs/phase-2-results/evidence/pinpoint-outbox-separation.png`

#### 🥉 보조 이미지 2: OutboxPollingPublisher 로그 (retry 증가 → 복구)

Kafka 가 다운된 동안 `build/phase2-logs/order.log` 에서 Outbox 발행 시도 실패 로그가 반복되다가 Kafka 복구 후 `markPublished` 로 전환되는 연속 로그를 그대로 스크린샷.

```
WARN Outbox event publish failed (retry 1/5): eventId=..., error=...
WARN Outbox event publish failed (retry 2/5): eventId=..., error=...
DEBUG Outbox event published: order.created (aggregate=Order/...)
```

저장: `docs/phase-2-results/evidence/outbox-retry-recovery-log.png`

#### 포트폴리오 삽입 예시

```markdown
### Phase 2 — Transactional Outbox 로 dual write 문제 제거

Kafka 브로커를 중지하고 주문 3건을 생성한 뒤 Order 서비스를 강제 종료, 재기동 — Phase 1 에서는 3건 전부 유실됐지만 Phase 2 는 Outbox 가 DB 에 보존해둔 덕분에 Kafka 복구 30초 내 자동 재발행.

![outbox_event 상태 전이](phase-2-results/evidence/outbox-state-transition.png)
```

### 정리

```bash
for port in 8081 8082 8083 8084; do
  pid=$(lsof -iTCP:$port -sTCP:LISTEN -P -n 2>/dev/null | awk 'NR>1 {print $2}' | head -1)
  [ -n "$pid" ] && kill $pid
done
```

---

## 다음 문제 (Phase 3 로 이어짐)

Phase 2 가 해결하지 못하는 것:
- **Kafka 의 at-least-once 전달**: 같은 이벤트가 두 번 이상 도착할 수 있음
- **다중 인스턴스 consumer**: 서로 다른 consumer group 이 동시에 같은 이벤트를 받으면 중복 결제가 생성될 수 있음 (`payment.order_id` 에 UNIQUE 제약 없음)

→ [Phase 3 — Idempotent Consumer](../phase-3-results/README.md)
