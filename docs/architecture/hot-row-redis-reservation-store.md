# Hot Row Contention 개선: Redis 재고 예약 저장소

## 목적

기존 atomic update 방식은 `product_variant`의 같은 row를 매 주문 요청마다 조건부 `UPDATE`로 갱신한다. 비관적 락보다 대기 시간은 줄지만, 인기 옵션 하나에 주문이 집중되면 여전히 DB hot row에 쓰기 부하가 몰린다.

Redis reservation store 방식은 주문 생성 시점의 재고 선점을 Redis 원자 연산으로 처리하고, DB 재고 차감은 결제 완료 확정 시점에 수행한다. Redis는 캐시가 아니라 결제 대기 중인 재고 예약 상태를 관리하는 쓰기 경로 저장소로 사용한다.

## 변경된 흐름

1. 사용자가 주문을 생성한다.
2. Order SAGA가 Product에 `orderId`, `variantId`, `quantity`로 재고 예약을 요청한다.
3. Product는 Redis에서 같은 옵션의 예약 합계를 원자적으로 확인하고, 가용 수량을 넘지 않을 때만 `orderId`별 예약을 기록한다.
4. Product는 DB에 `StockReservation(RESERVED)` 행을 남긴다.
5. Payment가 PG 승인에 성공하면 payment completed 이벤트가 발행된다.
6. Order SAGA는 주문 항목별 Product 예약을 `CONFIRMED`로 확정한다.
7. Product는 `product_variant.stock_quantity`를 조건부 UPDATE로 차감하고 Redis 예약을 제거한다.
8. Payment가 실패하면 Order SAGA는 Product 예약을 `RELEASED`로 바꾸고 Redis 예약을 제거한다.

## 상태 모델

| 상태 | 의미 | DB 재고 차감 여부 | Redis 예약 여부 |
| --- | --- | --- | --- |
| `RESERVED` | 주문 생성 후 결제 대기 | 미차감 | 존재 |
| `CONFIRMED` | 결제 완료 후 판매 확정 | 차감 | 제거 |
| `RELEASED` | 결제 실패 또는 보상 완료 | 미차감 | 제거 |

## 비교 기준

| 비교군 | 주문 생성 시점 재고 처리 | 기대 효과 |
| --- | --- | --- |
| 비관적 락 before | DB row `SELECT ... FOR UPDATE` 후 read-modify-write | hot row 대기 시간이 커지고 커넥션 점유가 길어진다. |
| atomic update after | DB 조건부 UPDATE 한 번으로 재고 차감 | 비관적 락보다 짧지만 인기 옵션 row 쓰기는 DB에 집중된다. |
| Redis reservation store | Redis 원자 예약 후 결제 완료 시 DB 차감 | 주문 생성 경로의 DB hot row 쓰기를 제거하고, 결제 확정 경로로 부하를 이동시킨다. |

## 검증

실행한 테스트:

```bash
./gradlew :service-product:test --tests '*ProductServiceRedisReservationTest' --tests '*ProductServiceStockTest'
./gradlew :service-order:test --tests '*OrderSagaOrchestratorTest' --tests '*ProductCatalogRestClientCircuitBreakerTest'
./gradlew :service-product:test :service-order:test
```

확인한 동작:

- 주문 예약은 DB 재고를 즉시 차감하지 않는다.
- 결제 완료 확정 시 DB 재고가 한 번만 차감된다.
- 같은 주문/옵션 확정 요청은 중복 차감되지 않는다.
- Redis 예약 합계가 DB 가용 수량을 넘는 요청은 거절된다.
- 결제 실패 보상은 DB 재고를 늘리지 않고 Redis 예약만 해제한다.
- Order SAGA의 Product 호출은 `orderId`를 포함해 예약, 확정, 해제를 수행한다.

## 남은 검증

부하 테스트에서는 같은 상품 옵션에 주문을 집중시켜 아래 값을 비교한다.

- 주문 생성 API p95, p99 지연 시간
- DB 커넥션 풀 active/pending 추이
- Product 서비스 HTTP server duration
- Redis CPU, memory, command latency
- 결제 완료 이벤트 처리량과 Product confirm 지연
- 실패 또는 타임아웃 시 `RESERVED` 예약이 과도하게 남지 않는지

이 방식은 주문 생성 경로의 병목을 줄이는 목적에는 적합하지만, 결제 완료 확정 트래픽이 매우 커질 경우 DB 차감 경로가 다시 병목이 될 수 있다. 그 경우에는 결제 완료 후 재고 확정을 배치 쓰기 또는 이벤트 기반 재고 원장 모델로 한 단계 더 분리해야 한다.
