# Phase 2 Outbox 패턴 — 검증 결과 및 남은 문제

## 테스트 환경
- 로컬 (MacOS), Phase 0/1과 동일 환경
- MySQL 8.0 + Kafka 3.8.1 (Docker Compose)
- 4개 서비스 `./gradlew bootRun` (local 프로파일)

---

## Phase 2가 해결한 문제: Kafka 다운 시 이벤트 유실

### 테스트 시나리오

```
1. 정상 주문 생성 → Outbox에 이벤트 저장 + Kafka 발행 확인 (is_published=1)
2. Kafka 중지 (docker stop ecommerce-kafka)
3. Kafka 없이 주문 3개 생성
4. Outbox 확인: is_published=0 (미발행 상태로 저장됨)
5. Kafka 재시작 (docker start ecommerce-kafka)
6. 15초 대기 후 Outbox 확인: is_published=1 (자동 발행됨)
7. Payment 테이블 확인: 결제 3건 전부 COMPLETED
```

### 결과

| 단계 | Phase 1 (직접 Kafka send) | Phase 2 (Outbox) |
|---|---|---|
| Kafka 다운 시 주문 생성 | 실패 또는 이벤트 유실 | **201 성공, Outbox에 저장** |
| Kafka 재시작 후 | 유실된 이벤트는 영원히 손실 | **자동 발행 + 결제 처리** |
| 이벤트 유실 | 발생 가능 | **0건** |

### 아키텍처 변화

```
Phase 1: Service → KafkaTemplate.send() → Kafka
         (Kafka 다운 시 이벤트 유실 — DB와 Kafka가 다른 트랜잭션)

Phase 2: Service → ApplicationEventPublisher → @TransactionalEventListener(BEFORE_COMMIT)
         → outbox_event INSERT (같은 DB 트랜잭션) → 커밋
         → OutboxPollingPublisher (500ms) → Kafka
         (DB 트랜잭션으로 원자성 보장 — 이벤트 유실 불가)
```

---

## Phase 2에서 남은 문제: 중복 이벤트 처리 (Phase 3 Situation)

### 문제 1: Consumer 측 중복 소비

Kafka는 at-least-once 전달을 보장한다. 즉, **같은 메시지가 2번 이상 도착할 수 있다.**

현재 PaymentService.processFromEvent()에 `existsByOrderIdAndStatus(orderId, COMPLETED)` 체크가 있지만:
- **단일 인스턴스에서는** Kafka consumer가 순차 처리라 race condition 없음 → 중복 방지 동작
- **다중 인스턴스 스케일 아웃 시** 두 consumer가 같은 파티션을 동시에 처리하면 둘 다 "없다"로 판단 → **중복 결제 생성 가능**

### 문제 2: Outbox Polling 중복 발행

다중 파드에서 같은 outbox_event row를 동시에 폴링하면:
- 파드 A: row 읽기 → Kafka 전송 → markPublished
- 파드 B: 같은 row 읽기 → Kafka 전송 → markPublished
- 결과: **같은 이벤트가 Kafka에 2번 발행됨**

현재 `@Version` 낙관적 락이 OutboxEvent에 있지만, 발행 후 markPublished 시점에만 충돌을 감지한다. 발행 자체는 이미 완료된 후.

### 테스트 증거

```
동일 이벤트를 5번 빠르게 Kafka에 수동 발행:
→ Payment 결과: 1건만 생성 (단일 인스턴스 순차 처리로 멱등성 체크 통과)
→ 하지만 이는 단일 인스턴스이기 때문. 다중 인스턴스에서는 보장 안 됨.
```

### Phase 3에서 해결할 것

1. **eventId 기반 중복 체크 테이블** (`processed_events`) — consumer가 이벤트 처리 전 eventId로 중복 확인
2. **Outbox @Version 낙관적 락 활용 강화** — 폴링 시 `OptimisticLockingFailureException` 처리로 단일 파드만 발행 보장
3. **Consumer 멱등성을 DB unique constraint로 강제** — race condition을 DB 레벨에서 차단

---

## STAR 요약

### Phase 2 해결

| | |
|---|---|
| **S** | Phase 1에서 KafkaTemplate.send()가 DB 트랜잭션 밖에 있어 Kafka 다운 시 이벤트 유실 |
| **T** | DB 저장과 이벤트 발행의 원자성 보장 |
| **A** | Outbox 패턴: 같은 트랜잭션에 outbox_event INSERT → Polling Publisher가 Kafka 발행 |
| **R** | Kafka 다운 상태에서 주문 3건 생성 → Kafka 복구 후 이벤트 3건 전부 자동 발행 → 이벤트 유실 0건 |

### Phase 3 Situation (다음 문제)

| | |
|---|---|
| **발견 방법** | 다중 인스턴스 시나리오 분석 + 동시 이벤트 발행 테스트 |
| **문제** | at-least-once 전달 + 다중 인스턴스 = 중복 이벤트 소비 가능 → 중복 결제, 재고 이중 차감 |
| **영향** | 데이터 정합성 훼손, 오버셀 |
