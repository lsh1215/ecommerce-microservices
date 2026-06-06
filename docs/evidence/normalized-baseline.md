# Evidence Normalized Baseline

## 목적

이번 evidence는 과거 커밋을 그대로 재현하는 historical replay가 아니라, 잘못된 공통 주문-재고 계약을 교정한 뒤 각 패턴의 효과를 비교하는 normalized baseline comparison이다.

기존 주문 생성 경로에는 Product 서비스를 두 번 호출하는 흐름이 있었다.

```text
Order -> Product: 상품 스냅샷 조회
Order -> Product: 재고 예약
```

이 구조는 SAGA, Outbox, Idempotency, Circuit Breaker, Hot Row Contention 중 어떤 패턴의 효과를 보려는지와 무관하게 모든 테스트에 공통으로 섞이는 baseline 오염 요인이었다. 특히 재고 예약은 command인데, Order가 Product 내부 절차를 `조회 후 예약`으로 나누어 조율하면 네트워크 왕복과 장애 전파 지점이 불필요하게 늘어난다.

따라서 새 evidence는 다음 공통 계약을 기준으로 한다.

```text
Order -> Product: 재고 예약 + 예약 시점 상품 스냅샷 반환
```

## 공통 API 계약

Product 내부 API는 모든 비교군에서 같은 의미를 가져야 한다.

| 항목 | 기준 |
| --- | --- |
| 요청 식별자 | `orderId` |
| 상품 옵션 | `variantId` |
| 예약 수량 | `quantity` |
| 응답 | 예약이 성공한 시점의 상품 스냅샷 |
| 멱등 기준 | `orderId + variantId` |

이 계약은 Redis reservation store뿐 아니라 pessimistic lock, atomic update 전략에서도 동일하게 유지한다. Hot Row Contention 비교에서는 Product 내부 저장소 전략만 다르게 두고 API 계약은 바꾸지 않는다.

## 동일하게 맞출 조건

새 evidence의 before/after 또는 전략 비교는 아래 조건을 공통으로 맞춘다.

| 영역 | 기준 |
| --- | --- |
| 결제 흐름 | PG 즉시 결제형 구조 |
| API 진입점 | Traefik gateway |
| 모니터링 | LGTM Stack |
| seed data | 테스트별 동일 seed |
| pod resource | 테스트별 동일 resource |
| 관측 설정 | 동일 OTel/Kafka/Loki/Tempo/Prometheus 설정 |
| Kafka 계약 | 같은 publisher/consumer topic 이름 |
| 주문-상품 계약 | single Product command API |

## 해석 기준

새 evidence와 archive된 과거 evidence를 직접 수치 비교하면 안 된다. 과거 evidence에는 Product 2회 호출 구조가 섞여 있고, 새 evidence는 이를 제거한 normalized baseline 위에서 재측정한다.

따라서 새 evidence의 비교 단위는 다음과 같다.

| 주제 | 비교 방식 |
| --- | --- |
| SAGA | normalized baseline 위에서 SAGA 적용 전/후 |
| Outbox | normalized baseline 위에서 Outbox 적용 전/후 |
| Idempotency + Optimistic Lock | normalized baseline 위에서 멱등 처리 적용 전/후 |
| Circuit Breaker | normalized baseline 위에서 Circuit Breaker 적용 전/후 |
| Hot Row Contention | pessimistic / atomic / redis 전략 비교 |

## 면접 방어 포인트

before까지 수정한 이유는 성능을 유리하게 보이기 위해서가 아니라, 비교 대상이 아닌 잘못된 공통 설계를 제거하기 위해서다. Product 2회 호출이 남아 있으면 SAGA나 Redis 같은 패턴의 효과보다 Product 왕복 호출 비용이 결과에 크게 섞인다. 그래서 새 evidence는 정상화된 주문-재고 계약을 먼저 고정하고, 그 위에서 각 패턴이 해결하려는 문제만 분리해서 측정한다.
