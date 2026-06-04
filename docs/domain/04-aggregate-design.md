# Aggregate Design

## 개요

각 Bounded Context의 Aggregate 경계, 불변식(Invariant), 일관성 규칙을 정의한다.

---

## 1. Product Context

### Product Aggregate

```
Product (Aggregate Root)
├── ProductVariant (Entity)  — Stock은 Variant의 속성
└── ProductImage (Entity)
```

- **Root**: `Product`
- **경계 내 Entity**: ProductVariant, ProductImage
- **불변식**:
  - Product.name은 비어있을 수 없다.
  - Product.price는 0 이상이어야 한다.
  - ProductVariant.sku는 시스템 전체에서 고유해야 한다.
  - ProductVariant.stockQuantity는 0 미만이 될 수 없다 (재고 예약 시 검증).
  - ProductImage 중 하나만 isPrimary = true일 수 있다.
- **일관성 규칙**:
  - 재고 예약/해제는 ProductVariant의 도메인 메서드(`reserveStock`, `releaseStock`)를 통해서만 수행한다.
  - Product 삭제 시 연관된 Variant와 Image도 cascade 삭제한다.
- **트랜잭션 경계**: Product와 하위 Entity는 하나의 트랜잭션으로 관리한다.

### Brand Aggregate

```
Brand (Aggregate Root, 독립)
```

- **Root**: `Brand`
- **불변식**:
  - Brand.name은 고유해야 한다.
  - Brand.name은 비어있을 수 없다.
- **관계**: Product는 Brand를 ManyToOne으로 참조하지만, 별도의 Aggregate로 독립적으로 생명주기를 관리한다.

---

## 2. Order Context

### Order Aggregate

```
Order (Aggregate Root)
├── OrderItem (Entity)
└── ShippingAddress (Value Object, @Embeddable)
```

- **Root**: `Order`
- **경계 내 Entity**: OrderItem
- **Value Object**: ShippingAddress
- **불변식**:
  - Order.orderNumber는 고유해야 한다 (ULID 기반 생성).
  - Order는 최소 1개의 OrderItem을 가져야 한다.
  - Order.totalAmount는 모든 OrderItem.totalPrice의 합과 일치해야 한다.
  - OrderStatus 전이는 허용된 경로만 가능하다:
    - PENDING → CONFIRMED → PAID → SHIPPING → DELIVERED
    - PENDING → CANCELLED, CONFIRMED → CANCELLED
    - PAID 이후 상태에서는 직접 CANCELLED로 전이할 수 없다.
  - OrderItem.quantity는 1 이상이어야 한다.
  - OrderItem.unitPrice는 0 이상이어야 한다.
- **일관성 규칙**:
  - 상태 전이는 Order의 도메인 메서드를 통해서만 수행한다.
  - OrderItem은 주문 생성 시점의 상품 정보를 스냅샷하여 저장한다 (Product Context와 분리).
  - ShippingAddress는 주문 생성 후 변경 불가 (Immutable).
- **트랜잭션 경계**: Order와 하위 OrderItem은 하나의 트랜잭션으로 관리한다.

---

## 3. Payment Context

### Payment Aggregate

```
Payment (Aggregate Root, 단일 Entity)
PaymentAttempt (PG 승인 요청 이력 Entity)
PaymentAttemptHistory (상태 변경 감사 이력 Entity)
```

- **Root**: `Payment`
- **불변식**:
  - Payment.orderId 당 결제는 최대 1건이다.
  - Payment.amount는 0보다 커야 한다.
  - PaymentStatus 전이는 허용된 경로만 가능하다:
    - PENDING → COMPLETED
    - PENDING → FAILED
    - COMPLETED → REFUNDED
  - REFUNDED는 최종 상태이며 더 이상 전이할 수 없다.
  - paidAt은 COMPLETED 전이 시에만 기록된다.
  - refundedAt은 REFUNDED 전이 시에만 기록된다.
- **일관성 규칙**:
  - 상태 전이는 Payment의 도메인 메서드를 통해서만 수행한다.
  - 동일 Order에 대해 COMPLETED 상태의 Payment가 있으면 새 결제를 생성할 수 없다.
  - PaymentAttempt는 PG 승인 요청, 처리 시작, 완료/실패 시각을 보존한다.
  - PaymentAttemptHistory는 상태 변경마다 append-only 감사 이력으로 저장한다.
  - 외부 PG adapter 호출은 Payment 트랜잭션 안에서 수행하지 않는다.
- **트랜잭션 경계**:
  - Payment/PaymentAttempt 생성과 이력 저장은 로컬 DB 트랜잭션으로 처리한다.
  - PaymentAttempt claim과 상태 전이는 짧은 로컬 트랜잭션으로 처리한다.
  - PG 승인 호출은 processor가 claim 트랜잭션을 끝낸 뒤 수행한다.

---

## 4. Customer Context

### Customer Aggregate

```
Customer (Aggregate Root)
└── CustomerAddress (Entity)
```

- **Root**: `Customer`
- **경계 내 Entity**: CustomerAddress
- **불변식**:
  - Customer.email은 시스템 전체에서 고유해야 한다.
  - Customer.email은 유효한 이메일 형식이어야 한다.
  - Customer.password는 BCrypt로 해시되어 저장된다 (평문 저장 금지).
  - Customer.name은 비어있을 수 없다.
  - CustomerAddress 목록에서 isDefault = true인 주소는 최대 1개이다.
- **일관성 규칙**:
  - 새 주소를 기본 배송지로 설정하면 기존 기본 배송지의 isDefault를 false로 변경한다.
  - Customer 삭제 시 연관된 Address도 cascade 삭제한다.
  - 비밀번호 변경은 Customer의 도메인 메서드를 통해서만 수행한다.
- **트랜잭션 경계**: Customer와 하위 Address는 하나의 트랜잭션으로 관리한다.

---

## Aggregate 간 참조 규칙

| 참조 방향 | 방식 | 설명 |
|-----------|------|------|
| Order → Product | ID 참조 (productId, productVariantId) | OrderItem에 Product 정보를 스냅샷으로 저장. 직접 Entity 참조 없음. |
| Order → Customer | ID 참조 (customerId) | 주문 생성 시 Customer 존재 여부만 검증. |
| Payment → Order | ID 참조 (orderId, orderNumber) | 결제 대상 Order를 ID로만 참조. |
| Product → Brand | JPA ManyToOne | 동일 Context 내이므로 직접 참조 허용. |
| Customer → CustomerAddress | JPA OneToMany | 동일 Aggregate 내이므로 직접 참조. |

---

## 설계 결정 사항

1. **Stock을 별도 Aggregate로 분리하지 않는 이유**: 재고는 ProductVariant의 속성이며, 동시성 제어는 DB 수준 비관적 잠금으로 처리한다. 별도 서비스(Inventory)를 두지 않아 복잡도를 줄인다.

2. **OrderItem에 상품 정보를 스냅샷하는 이유**: 주문 이후 상품 가격이나 이름이 변경되어도 주문 당시의 정보를 보존해야 한다. Product Context와 Order Context의 결합도를 낮춘다.

3. **Payment를 단일 Entity Aggregate로 유지하는 이유**: 결제 도메인이 단순하며(stub 기반), 부분 결제나 분할 결제를 지원하지 않으므로 단일 Entity로 충분하다.

4. **ShippingAddress를 Embeddable Value Object로 설계하는 이유**: 주문의 배송지는 주문과 생명주기가 동일하며, 독립적인 식별자가 필요 없다. CustomerAddress와 구조는 유사하지만 별도의 스냅샷이다.
