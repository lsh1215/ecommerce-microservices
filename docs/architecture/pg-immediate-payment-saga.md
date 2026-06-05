# PG 즉시 결제형 SAGA 설계

## 목적

기존 주문 생성 중심 결제 흐름을 PG 즉시 결제형 흐름으로 바꾼다. 핵심은 주문 생성 트랜잭션 안에서 외부 PG를 호출하지 않고, 결제 요청과 처리 결과를 DB 이력으로 남긴 뒤 별도 processor가 PG 승인 adapter를 호출하는 것이다.

## 변경 전

- Order SAGA가 재고 예약 후 결제 안내 응답을 Order에 포함했다.
- Payment는 `order.created` 이벤트를 받아 결제 성공/실패를 바로 결정했다.
- 결제 요청 시각, 처리 시작 시각, 완료/실패 시각을 별도 행으로 추적하기 어려웠다.
- 외부 PG 연동으로 확장할 때 트랜잭션 경계와 재시도 기준이 모호했다.

## 변경 후

- Order는 재고 예약 후 `order.created` 이벤트만 발행한다.
- Payment는 `Payment`와 `PaymentAttempt`를 생성하고 REQUESTED 이력을 저장한다.
- `PaymentAttemptProcessor`가 claim 트랜잭션을 끝낸 뒤 `PaymentGatewayPort`를 호출한다.
- processor는 한 번 깨어날 때 기본 최대 50건까지 처리한다. 설정 키는 `application.payment-attempt-processor.max-batch-size`이다.
- 승인 성공 시 Payment/Attempt를 COMPLETED로 바꾸고 `payment.completed` 이벤트를 발행한다.
- 승인 실패 시 retry 가능 여부에 따라 RETRYABLE_FAILED 또는 FAILED 이력을 남긴다.
- Order SAGA는 `payment.completed`를 받으면 CONFIRMED, PAID로 전이하고, `payment.failed`를 받으면 주문 취소와 재고 해제 보상을 진행한다.

## 트랜잭션 경계

| 단계 | 트랜잭션 안에서 수행 | 트랜잭션 밖에서 수행 |
| --- | --- | --- |
| 주문 생성 | Order, SagaInstance 저장 | Product 재고 예약 HTTP 호출 |
| 재고 예약 완료 | OrderItem 저장, Saga 전이, `order.created` outbox 저장 | 없음 |
| 결제 요청 생성 | Payment, PaymentAttempt, REQUESTED history 저장 | 없음 |
| 결제 attempt claim | Attempt PROCESSING 전이, PROCESSING_STARTED history 저장 | 없음 |
| PG 승인 | 없음 | `PaymentGatewayPort.authorize()` 호출 |
| 결제 결과 반영 | Payment/Attempt 상태 전이, history 저장, payment event outbox 저장 | 없음 |
| 결제 실패 보상 | 주문 취소, Saga 보상 시작 상태 저장 | Product 재고 해제 HTTP 호출 |

## 체크리스트

| 항목 | 기준 | 상태 |
| --- | --- | --- |
| 외부 PG 호출 분리 | `PaymentService`의 `@Transactional` 메서드에서 gateway adapter를 직접 호출하지 않는다. | 통과 |
| 결제 요청 이력 | `PaymentAttempt`에 요청/처리/완료/실패 시각과 retry count를 남긴다. | 통과 |
| 감사 이력 | `PaymentAttemptHistory`에 REQUESTED, PROCESSING_STARTED, COMPLETED, FAILED, RETRYABLE_FAILED, CANCELLED를 append한다. | 통과 |
| 중복 주문 이벤트 | 동일 orderId의 Payment는 DB unique key와 애플리케이션 가드로 중복 생성하지 않는다. | 통과 |
| attempt claim 경쟁 | claim 조회에 pessimistic write lock을 적용한다. | 통과 |
| batch 처리 | scheduler tick당 기본 50건까지 attempt를 처리한다. | 통과 |
| SAGA 보상 | 결제 실패 시 Order 취소와 Product 재고 해제 보상을 수행한다. | 통과 |
| 완료 결제 취소 | COMPLETED Payment는 REFUNDED로 전이하고 완료된 Attempt는 CANCELLED 이력으로 오염시키지 않는다. | 통과 |
| 결제 안내 응답 제거 | Order 응답에서 결제 안내 전용 필드를 제거한다. | 통과 |

## 검증 명령

```bash
cd /Users/leesanghun/My_Project/ecommerce-microservices-worktrees/pg-immediate-payment-saga/backend-v2
./gradlew :service-payment:test
./gradlew :service-order:test
```

## 관련 산출물

- 시스템 아키텍처: `docs/architecture/pg-immediate-payment-system.excalidraw`
- 이벤트 스토밍: `docs/architecture/pg-immediate-payment-event-storming.html`
