# Bounded Context Map

## 개요

4개의 Bounded Context 간 관계와 통신 패턴을 정의한다.

---

## Context Map Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        E-Commerce Platform                      │
│                                                                 │
│   ┌──────────────┐          sync (RestClient)    ┌───────────┐  │
│   │              │◄─────────────────────────────  │           │  │
│   │   Product    │   재고 예약/해제, 상품 조회     │           │  │
│   │   Context    │                                │   Order   │  │
│   │   (8081)     │                                │   Context │  │
│   │              │                                │   (8082)  │  │
│   └──────────────┘                                │           │  │
│                                                   │           │  │
│   ┌──────────────┐          async (Kafka)         │           │  │
│   │              │◄───────────────────────────────│           │  │
│   │   Payment    │   order.created                │           │  │
│   │   Context    │──────────────────────────────► │           │  │
│   │   (8083)     │   payment.completed/failed     └───────────┘  │
│   │              │                                       │       │
│   └──────────────┘                                       │       │
│                                                   sync   │       │
│   ┌──────────────┐          (RestClient)                 │       │
│   │              │◄──────────────────────────────────────┘       │
│   │   Customer   │   고객 검증                                    │
│   │   Context    │                                               │
│   │   (8084)     │                                               │
│   │              │                                               │
│   └──────────────┘                                               │
└─────────────────────────────────────────────────────────────────┘
```

---

## Context 간 관계

### 1. Order → Product (Customer-Supplier)

- **관계 유형**: Customer-Supplier (Order가 Customer, Product가 Supplier)
- **통신 방식**: 동기 (RestClient)
- **호출 목적**:
  - 주문 생성 시 상품 정보 조회 (`GET /api/products/{id}`)
  - 재고 예약 (`POST /api/products/variants/{variantId}/reserve-stock`)
  - 주문 취소 시 재고 해제 (`POST /api/products/variants/{variantId}/release-stock`)
- **데이터 흐름**: Order Context는 ProductId, ProductVariantId로 Product Context에 요청하고, 상품 정보를 OrderItem에 스냅샷으로 저장한다.

### 2. Order ↔ Payment (Partnership)

- **관계 유형**: Partnership (양방향 비동기 이벤트)
- **통신 방식**: 비동기 (Kafka)
- **이벤트 흐름**:
  - Order → Payment: `order.created` (주문 생성 → 결제 자동 시작)
  - Order → Payment: `order.cancelled` (주문 취소 → 결제 환불)
  - Payment → Order: `payment.completed` (결제 완료 → 주문 상태 PAID로 변경)
  - Payment → Order: `payment.failed` (결제 실패 → 주문 취소 처리)

### 3. Order → Customer (Customer-Supplier)

- **관계 유형**: Customer-Supplier (Order가 Customer, Customer Context가 Supplier)
- **통신 방식**: 동기 (RestClient)
- **호출 목적**:
  - 주문 생성 시 고객 존재 여부 검증 (`GET /api/customers/{id}`)
- **데이터 흐름**: Order Context는 CustomerId로 Customer Context에 조회 요청을 보낸다.

### 4. Product Context (독립)

- Product Context는 다른 Context를 호출하지 않는다.
- 재고 예약/해제 요청을 수신만 한다.

### 5. Customer Context (독립)

- Customer Context는 다른 Context를 호출하지 않는다.
- 고객 검증 요청을 수신만 한다.
- 회원 가입 시 `customer.registered` 이벤트를 Kafka로 발행한다.

---

## 통신 패턴 요약

| 출발 Context | 도착 Context | 방식 | Kafka Topic / API |
|-------------|-------------|------|-------------------|
| Order | Product | Sync (RestClient) | `GET /api/products/{id}`, `POST .../reserve-stock`, `POST .../release-stock` |
| Order | Customer | Sync (RestClient) | `GET /api/customers/{id}` |
| Order | Payment | Async (Kafka) | `order.created`, `order.cancelled` |
| Payment | Order | Async (Kafka) | `payment.completed`, `payment.failed` |
| Customer | — | Async (Kafka) | `customer.registered` |
| Product | — | Async (Kafka) | `product.stock-reserved`, `product.stock-released` |

---

## Anti-Corruption Layer

- Order Context는 Product/Customer의 응답 DTO를 직접 도메인 모델에 바인딩하지 않는다.
- `infra/client/` 패키지에 RestClient를 두고, 외부 응답을 내부 도메인이 필요한 형태로 변환한다.
- Kafka 이벤트는 common 모듈의 `DomainEvent` 기반 클래스를 사용하여 직렬화/역직렬화 일관성을 보장한다.
