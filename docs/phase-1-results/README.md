# Phase 1 Event-Driven SAGA — 부하 테스트 결과 및 Phase 0 비교

## 테스트 환경
- 로컬 (MacOS), Phase 0과 동일 환경
- MySQL 8.0 + Kafka 3.8.1 (Docker Compose)
- 4개 서비스 `./gradlew bootRun` (local 프로파일)
- k6 로컬 실행

---

## 1. 정상 상태 (4 서비스 UP)

**k6 설정**: 1 VU, 10초, smoke 테스트

| 지표 | Phase 0 (동기) | Phase 1 (비동기) | 변화 |
|---|---|---|---|
| 주문 생성 성공률 | 100% | **100%** | 동일 |
| 에러율 | 0% | **0%** | 동일 |
| http_req_duration p(95) | 95.38ms | **57.87ms** | **39% 개선** |
| http_req_duration p(99) | 136.03ms | **89.13ms** | **34% 개선** |
| http_req_duration avg | 30.34ms | **22.92ms** | **24% 개선** |

**분석**: 정상 상태에서도 응답 시간이 개선됐다. Phase 0은 주문 생성 시 Payment 서비스에 동기 HTTP 호출 후 응답을 기다렸지만, Phase 1은 Kafka에 이벤트만 발행하고 즉시 PENDING으로 반환한다. 동기 HTTP 왕복 시간이 제거된 효과.

결과 파일: `k6-normal-load.txt`

---

## 2. 장애 상태 — Payment 서비스 DOWN

**k6 설정**: 5 VUs, 30초

### Before/After 비교

| 지표 | Phase 0 (동기) | Phase 1 (비동기) | 변화 |
|---|---|---|---|
| 주문 생성 성공률 | **0%** (140건 전부 실패) | **67%** (98/145 성공) | **0% → 67%** |
| 전체 에러율 | 33.33% | **10.80%** | **22.5%p 감소** |
| http_req_duration p(95) | 68.33ms | **43.05ms** | **37% 개선** |
| Order 서비스 응답 | 500 에러 반환 | **201 PENDING 반환** | **장애 격리 성공** |

### 핵심 발견

**Phase 0**: Payment 서비스 DOWN → Order 서비스가 동기 HTTP 호출에서 실패 → 주문 생성 100% 실패. **Cascading Failure**.

**Phase 1**: Payment 서비스 DOWN → Order 서비스는 Kafka에 이벤트만 발행하고 PENDING으로 반환 → **주문 생성은 성공**. 결제는 Payment 복구 후 Kafka 이벤트 재처리로 완료.

**67% 성공 (100%가 아닌 이유)**: 주문 생성 시 재고 예약은 여전히 Product 서비스에 동기 호출한다. 일부 요청이 재고 부족(이전 테스트에서 차감된 재고)으로 실패한 것. 이건 Payment 장애와 무관한 실패이며, Phase 1이 해결하려는 문제(Payment cascading failure)는 완벽히 해결됐다.

### 아키텍처 변화 효과

```
Phase 0:
  Order --sync HTTP--> Payment (DOWN)
  결과: Order 500 에러 (Payment 응답 대기 → 타임아웃 → 실패)

Phase 1:
  Order --Kafka event--> Kafka broker (UP)
  결과: Order 201 PENDING (Kafka에 이벤트 적재, Payment 복구 시 처리)
```

Payment 서비스 장애가 Order 서비스로 전파되지 않는다 = **장애 격리(Fault Isolation) 달성**.

결과 파일: `k6-cascading-failure.txt`

---

## STAR 정리

### S (Situation)
Phase 0 MVP에서 Payment 서비스 장애 시 주문 생성이 100% 실패하는 cascading failure 확인. Order 서비스가 Payment를 동기 HTTP로 호출하기 때문에 Payment 장애가 그대로 전파.

### T (Task)
Order→Payment 통신을 비동기 이벤트 기반으로 전환하여, Payment 장애 시에도 주문 생성이 가능하도록 장애 격리.

### A (Action)
Kafka 이벤트 도입 + Orchestration SAGA 패턴 적용.
- Order 서비스: `order.created` 이벤트 발행 후 즉시 PENDING 반환
- Payment 서비스: `order.created` 이벤트를 비동기 소비하여 결제 처리
- SAGA Orchestrator: `payment.completed/failed` 이벤트를 받아 주문 상태 전이 + 보상 트랜잭션

### R (Result)
| 지표 | Before (Phase 0) | After (Phase 1) |
|---|---|---|
| Payment DOWN 시 주문 성공률 | 0% | **67%+** (재고 무관 시 100%) |
| 정상 시 p95 응답 | 95ms | **58ms** (39% 개선) |
| 장애 격리 | 없음 (cascading failure) | **달성** (Payment 독립) |

---

## 남은 문제 (후속 Phase에서 해결)

1. **이벤트 발행 신뢰성**: Kafka 전송 실패 시 이벤트 유실 가능 → Phase 2 (Outbox 패턴)
2. **중복 이벤트 처리**: Kafka at-least-once로 중복 소비 가능 → Phase 3 (멱등성)
3. **재고 예약 동기 호출**: Product 서비스 장애 시 주문 생성 실패 → Phase 4 (Circuit Breaker)
