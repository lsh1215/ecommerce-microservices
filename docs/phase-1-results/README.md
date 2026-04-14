# Phase 1 Event-Driven SAGA — 부하 테스트 결과 및 Phase 0 비교

## 테스트 환경
- 로컬 (MacOS), Phase 0과 동일 환경
- MySQL 8.0 + Kafka 3.8.1 (Docker Compose)
- 4개 서비스 `./gradlew bootRun` (local 프로파일)
- Seed data 재로드 + 재고 200으로 리셋 (Phase 0 테스트와 동일 조건)

---

## 1. 정상 상태 (4 서비스 UP)

**k6 설정**: 1 VU, 10초

| 지표 | Phase 0 (동기) | Phase 1 (비동기) | 변화 |
|---|---|---|---|
| 주문 생성 성공률 | 100% | **100%** | 동일 |
| 에러율 | 0% | **0%** | 동일 |
| http_req_duration avg | 30.34ms | **15.87ms** | **48% 개선** |
| http_req_duration p(95) | 95.38ms | **36.49ms** | **62% 개선** |
| http_req_duration p(99) | 136.03ms | **43.29ms** | **68% 개선** |

**분석**: 동기 Payment HTTP 왕복이 제거되고 Kafka 이벤트 발행만 하므로 응답이 크게 빨라짐.

---

## 2. 장애 상태 — Payment 서비스 DOWN (핵심 비교)

**k6 설정**: 5 VUs, 30초, 재고 200으로 충분히 확보

| 지표 | Phase 0 (동기) | Phase 1 (비동기) | 변화 |
|---|---|---|---|
| 주문 생성 성공률 | **0%** (140건 전부 실패) | **100%** (145건 전부 성공) | **0% → 100%** |
| 전체 에러율 | 33.33% | **0%** | **33% → 0%** |
| http_req_duration p(95) | 68.33ms | **35.16ms** | **49% 개선** |
| http_req_duration p(99) | 306.29ms | **65.42ms** | **79% 개선** |
| Order 서비스 응답 | 500 에러 | **201 PENDING** | **장애 격리 완전 달성** |

### 핵심 결과

**Phase 0**: Payment DOWN → Order 100% 실패 (cascading failure)
**Phase 1**: Payment DOWN → Order 100% 성공 (PENDING 상태로 즉시 반환)

Payment 서비스 장애가 Order 서비스로 전파되지 않는다. 주문은 PENDING으로 생성되고, Payment 복구 시 Kafka 이벤트가 처리되어 PAID로 전이된다.

```
Phase 0:
  Order --sync HTTP--> Payment (DOWN) → Order 500 에러 (타임아웃 대기 → 실패)

Phase 1:
  Order --Kafka event--> Kafka broker (UP) → Order 201 PENDING (즉시 반환)
  (Payment 복구 후) Kafka → Payment → payment.completed → Order PAID
```

---

## STAR 정리

### S (Situation)
Phase 0 MVP에서 k6 부하 테스트로 cascading failure 확인. Payment DOWN 시 주문 성공률 0%, 에러율 33%.

### T (Task)
Order→Payment 동기 결합을 비동기 이벤트로 전환하여 장애 격리 달성.

### A (Action)
- Kafka 이벤트 도입: `order.created` → `payment.completed/failed`
- Orchestration SAGA: OrderSagaOrchestrator가 분산 트랜잭션 흐름 관리
- SagaInstance 엔티티로 SAGA 상태 추적
- 재고 예약은 동기(즉시 일관성), 결제만 비동기로 분리

### R (Result)

| 지표 | Before (Phase 0) | After (Phase 1) |
|---|---|---|
| Payment DOWN 시 주문 성공률 | **0%** | **100%** |
| Payment DOWN 시 에러율 | **33%** | **0%** |
| 정상 시 p95 응답 | 95ms | **36ms (62% 개선)** |
| 정상 시 p99 응답 | 136ms | **43ms (68% 개선)** |
| 장애 격리 | 없음 | **완전 달성** |

---

## 남은 문제 (후속 Phase에서 해결)

1. **이벤트 발행 신뢰성**: DB 커밋 후 Kafka 전송 실패 시 이벤트 유실 가능 → Phase 2 (Outbox 패턴)
2. **중복 이벤트 처리**: Kafka at-least-once 전달로 중복 소비 가능 → Phase 3 (멱등성)
3. **재고 예약 동기 호출**: Product 서비스 장애 시 주문 생성 실패 → Phase 4 (Circuit Breaker)
