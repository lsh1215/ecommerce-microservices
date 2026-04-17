# Phase 3 — Idempotent Consumer (중복 이벤트 차단)

- **Worktree**: main 브랜치 (`a734c32` 이후) — Phase 3 의 증명은 multi-consumer-group race 이므로 **main 에서 toggle 을 켜고/꺼서** 측정.
- **Evidence**:
  - [`evidence/multi-consumer-off-*.txt`](./evidence/) — `--guards off` (Before: 중복 발생)
  - [`evidence/multi-consumer-on-*.txt`](./evidence/) — `--guards on` (After: exactly-once)
- **METHOD**: [`METHOD.md`](./METHOD.md) — 다중 인스턴스 재현 전략 상세

## 문제 정의 (Problem)

[Phase 2](../phase-2-results/README.md) 에서 Outbox 가 이벤트 유실을 막아줬지만, Kafka 는 본질적으로 **at-least-once** 전달. 같은 이벤트가 두 번 이상 전달될 수 있는 시나리오:

1. **Producer 재시도**: Kafka ack timeout 후 동일 이벤트 재전송 (outbox poller 의 `retry_count` 로직이 이에 해당)
2. **Consumer rebalance**: consumer 가 offset commit 전에 재시작 → 같은 이벤트 재소비
3. **다중 consumer group**: 서로 다른 그룹이 같은 토픽 구독 → 각 그룹이 독립적으로 이벤트 수신

현재 코드는 `PaymentService.processFromEvent` 에 `existsByOrderIdAndStatus(orderId, COMPLETED)` 체크가 있지만 이건 TOCTOU 레이스에 취약하다. 두 인스턴스가 동시에 같은 이벤트를 받으면 둘 다 "아직 없음"으로 판단 → **중복 payment 행 생성**.

`payment.order_id` 에는 UNIQUE 제약도 없으므로 DB 가 막아주지도 않는다.

## 해결 방법 (Solution)

**`processed_event` 테이블 + `IdempotentEventHandler` 래퍼.**

```java
// backend-v2/common/src/main/java/com/ecommerce/common/idempotency/IdempotentEventHandler.java
@Transactional
public boolean tryProcess(String eventId, String eventType, Runnable processor) {
    if (processedEventRepository.existsByEventId(eventId)) {
        return false;   // fast path — 이미 처리됨
    }
    processor.run();     // 비즈니스 로직 실행 (예: payment.save)
    try {
        processedEventRepository.saveAndFlush(ProcessedEvent.of(eventId, eventType));
    } catch (DataIntegrityViolationException e) {
        // safety net — 다른 인스턴스가 동시에 commit → @Transactional 롤백
        log.info("concurrent duplicate detected (DB constraint): eventId={}", eventId);
    }
    return true;
}
```

핵심:
1. **`processed_event.event_id` UNIQUE 제약** — DB 가 중복 commit 을 막는 최종 방어선
2. **`@Transactional` 롤백 연쇄** — `saveAndFlush` 가 UNIQUE 위반으로 실패하면 같은 트랜잭션 안의 `processor.run()` 도 함께 롤백 → payment 저장 취소
3. 결과적으로 **exactly-once** 효과 (at-least-once delivery + idempotent handler)

### 구현 포인트

| 파일 | 역할 |
|---|---|
| `backend-v2/common/src/main/java/com/ecommerce/common/idempotency/IdempotentEventHandler.java` | 멱등 wrapper |
| `backend-v2/common/src/main/java/com/ecommerce/common/idempotency/ProcessedEvent.java` | `@Column(unique=true) eventId` |
| `backend-v2/common/src/main/java/com/ecommerce/common/idempotency/ProcessedEventRepository.java` | JPA repository |
| `backend-v2/service-payment/.../OrderEventConsumer.java` | `idempotentEventHandler.tryProcess(eventId, "order.created", () -> paymentService.processFromEvent(...))` |

### 두 가지 toggle (Phase 3 증명용, 운영 금지)

증명을 위해 두 guard 를 runtime 에 끄는 flag 를 추가했다. **운영 기본값은 둘 다 true**.

| 프로퍼티 | 파일 | off 일 때 |
|---|---|---|
| `application.idempotency.enabled` | `IdempotentEventHandler.java` | `existsByEventId` 체크 + `processed_event` insert 양쪽 스킵 |
| `application.business-idempotency-guard.enabled` | `PaymentService.java` | `existsByOrderIdAndStatus(orderId, COMPLETED)` 체크 스킵 |

harness 스크립트의 `--guards off` 는 두 flag 를 동시에 false 로 설정해 **Phase 3 이전 코드의 행동을 정확히 재현**한다.

## Before / After 핵심 수치

| 시나리오 | `--guards off` (Before) | `--guards on` (After) |
|---|---|---|
| Payment 총 행 수 | 6,730 | 2,401 |
| 고유 `order_id` 수 | 3,354 | 2,401 |
| `order_id` 당 payment 평균 | **≈ 2.0 (두 consumer group 양쪽 다 씀)** | **1.0 (exactly-once)** |
| `SELECT order_id, COUNT(*) FROM payment GROUP BY order_id HAVING COUNT(*) > 1` | 3,341 rows | **0 rows** |

수치 근거: [`evidence/multi-consumer-off-20260418-041054.txt`](./evidence/) · [`evidence/multi-consumer-on-20260418-041203.txt`](./evidence/).

---

## 🧪 Testing Guide

### 1. 테스트 종류

| 항목 | 내용 |
|---|---|
| **유형** | Multi-instance race 재현 (Kafka consumer group 2개 동시 실행) |
| **부하 생성기** | `scripts/phase3-multi-consumer-test.sh` (수동 curl 루프 N회 POST) |
| **재현 트릭** | 서로 다른 `group-id` 를 가진 두 Payment 인스턴스 (`payment-group-a` :8083, `payment-group-b` :8183). Kafka 는 각 group 에게 **모든 메시지를 독립적으로 배달** → 두 consumer 가 동일 이벤트 수신 → race. |
| **검증 지표** | `SELECT COUNT(*) FROM payment GROUP BY order_id HAVING COUNT(*) > 1` |

> 단일 consumer group 에 두 인스턴스를 띄우면 Kafka 가 partition 을 쪼개서 배당해 각 인스턴스는 절반씩만 본다 → race 가 일어나지 않음. 그래서 **그룹 분리** 가 핵심.

### 2. 실행 방법

#### Step A. main 에서 `--guards off` 로 Before 재현

```bash
cd /Users/leesanghun/My_Project/ecommerce-microservices

# main 이므로 Phase 3 + 4 + 5 의 모든 개선이 포함됨
# 하지만 --guards off 는 idempotency 경로를 우회해 Phase 2 상태 재현

# 인프라 + Pinpoint + Order 서비스 기동 (Payment 는 harness 가 직접 띄움)
docker compose -f infra/docker-compose.yml up -d mysql kafka
docker compose -f monitoring/docker-compose.pinpoint.yml up -d

# Order / Product / Customer 기동 (Pinpoint 부착)
(cd backend-v2 && ./gradlew :service-product:bootJar :service-order:bootJar :service-customer:bootJar -x test -q)

for svc in product order customer; do
  ports=(8081 8082 8084); idx=$( [[ $svc == product ]] && echo 0 || [[ $svc == order ]] && echo 1 || echo 2 )
  jar=$(ls backend-v2/service-${svc}/build/libs/service-${svc}-*.jar | grep -v plain | head -1)
  java \
    -javaagent:pinpoint-agent/pinpoint-bootstrap.jar \
    -Dpinpoint.agentId=svc-${svc}-main \
    -Dpinpoint.applicationName=service-${svc} \
    -Dpinpoint.config=pinpoint-agent/pinpoint-root.config \
    -Dprofiler.transport.grpc.collector.ip=localhost \
    -jar $jar --spring.profiles.active=local > /tmp/phase3-${svc}.log 2>&1 &
done
sleep 30

# 시드
docker exec -i ecommerce-mysql mysql -uroot -p1234 < scripts/seed-data.sql
docker exec ecommerce-mysql mysql -uroot -p1234 -e \
  "USE ecommerce_product; UPDATE product_variant SET stock_quantity = 100000 WHERE id IN (1,2,3,4,5);"

# harness: Payment 인스턴스 2개 + 주문 50건 POST + 중복 카운트
./scripts/phase3-multi-consumer-test.sh --guards off --orders 50
```

harness 출력이 `docs/phase-3-results/evidence/multi-consumer-off-<TS>.txt` 에 자동 저장됨.

#### Step B. `--guards on` 으로 After 증명

```bash
./scripts/phase3-multi-consumer-test.sh --guards on --orders 50
```

harness 는 같은 시나리오를 반복하되 두 toggle 을 모두 true 로 설정한다. 출력은 `multi-consumer-on-<TS>.txt`.

### 3. 확인 지표

| 지표 | 출처 | Before | After |
|---|---|---|---|
| `payment` 테이블에서 `COUNT > 1 per order_id` row 수 | MySQL | **> 0** (대부분의 주문이 중복) | **0** |
| Pinpoint Server Map | `:8079` | `service-payment-phase3-groupA` + `service-payment-phase3-groupB` 둘 다 `order.created` topic 에서 consume | 동일, 단 한 쪽 transaction 에서 `DataIntegrityViolationException` 발생 |
| Payment 로그 `DataIntegrityViolationException` 건수 | `/tmp/phase3-logs/pay-*.log` | 0 | **> 0** (idempotency safety net 이 작동) |
| 두 인스턴스의 Application 별 transaction count | Pinpoint Inspector | 거의 동일 (둘 다 모든 이벤트 소비) | `group-a` 가 커밋 성공, `group-b` 는 롤백 (또는 반대) — **한쪽만 실제 payment.save 반영** |

### 4. 포트폴리오 증거 캡처

#### 🥇 대표 이미지: DB 쿼리 결과 — 중복 카운트 Before/After

DBeaver 로 아래 쿼리를 두 실행 후 각각 캡처해 나란히 배치.

```sql
SELECT
  order_id,
  COUNT(*) AS payment_rows,
  GROUP_CONCAT(status) AS statuses
FROM ecommerce_payment.payment
GROUP BY order_id
ORDER BY payment_rows DESC
LIMIT 20;
```

- Before: 대부분 `payment_rows = 2`
- After: 모든 `payment_rows = 1`

저장: `docs/phase-3-results/evidence/dup-count-before-after.png`

> 💡 캡션: _"동일한 order.created 이벤트를 두 consumer group 이 동시에 수신해도 processed_event UNIQUE 제약 + @Transactional 롤백이 정확히 한 건의 payment 만 남긴다."_

#### 🥈 보조 이미지 1: Pinpoint Server Map — 2 consumer race

Pinpoint `http://localhost:8079` 에서 Application 드롭다운에 `service-payment-phase3-groupA` 와 `...-groupB` 둘 다 표시. 두 Application 이 동시에 `order.created` 토픽을 consume 하는 에지가 보이도록 시간창을 부하 구간에 맞춤.

저장: `docs/phase-3-results/evidence/pinpoint-two-consumers.png`

#### 🥉 보조 이미지 2: `DataIntegrityViolationException` 로그 — safety net 발동

```bash
grep -E 'DataIntegrityViolationException|concurrent duplicate|동시 중복' \
  /tmp/phase3-logs/pay-a.log /tmp/phase3-logs/pay-b.log | head -20
```

두 인스턴스 중 **늦게 commit 한 쪽** 의 로그에 `DataIntegrityViolationException` 이 반복 찍히는 모습을 캡처.

저장: `docs/phase-3-results/evidence/safety-net-log.png`

#### 포트폴리오 삽입 예시

```markdown
### Phase 3 — Exactly-once via processed_event UNIQUE + @Transactional

두 개의 Kafka consumer group 이 같은 `order.created` 이벤트를 동시에 수신하도록 의도적으로 설계한 harness 에서:

| 지표 | `--guards off` | `--guards on` |
|---|---|---|
| payment 총 행 수 | 6,730 | **2,401** |
| `order_id` 당 평균 | ≈ 2.0 | **1.0** |
| 중복 order_id 수 | 3,341 | **0** |

![중복 카운트 Before/After](phase-3-results/evidence/dup-count-before-after.png)

DB 레벨 UNIQUE 위반으로 한쪽 트랜잭션 전체가 롤백되므로 payment 저장도 함께 취소된다.
```

### 정리

harness 는 종료 시 자동으로 Payment 인스턴스 두 개를 `kill` 한다. Order/Product/Customer 는 수동 종료:

```bash
for port in 8081 8082 8084 8183; do
  pid=$(lsof -iTCP:$port -sTCP:LISTEN -P -n 2>/dev/null | awk 'NR>1 {print $2}' | head -1)
  [ -n "$pid" ] && kill $pid
done
```

---

## 다음 문제 (Phase 4 로 이어짐)

Phase 3 로 Order ↔ Payment 비동기 경로는 exactly-once. 하지만 **동기 경로가 남아있다**:

- Order → Product (재고 예약): 강한 일관성 필요 — 비동기화 불가
- Order → Customer (프로필 조회): 동기

Product 가 **느려지면** (다운이 아니라) Order Tomcat 스레드 풀이 전부 Product 응답 대기로 block 됨 → 주문과 무관한 엔드포인트까지 응답 지연 → **thread exhaustion cascading failure**.

→ [Phase 4 — Circuit Breaker](../phase-4-results/README.md)
