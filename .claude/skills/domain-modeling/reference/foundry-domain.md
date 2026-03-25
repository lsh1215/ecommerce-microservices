# FOUNDRY Domain Reference

## Bounded Context Map

| Context | Aggregate Roots | Children | Value Objects | Type |
|---------|----------------|----------|---------------|------|
| **Product** | `Product`, `Brand` | Variant, Image, Translation | Money, Measurement | Core |
| **Drop** | `DropEvent` | DropProduct, DropStatusHistory | DropSchedule, AllocationQuantity | Core |
| **Order** | `Orders` | OrderItem, OrderStatusHistory | Money, OrderStatus, IdempotencyKey | Core |
| **Payment** | `Payment` | PaymentEvent | Money, PaymentStatus | Generic |
| **Inventory** | `Inventory` | InventoryEvent | StockQuantity | Supporting |
| **Customer** | `Customer` | CustomerAddress | Email, HashedPassword, Address | Generic |

## Context Relationships

```
[Product] <--Conformist-- [Drop]
[Order]   --Customer/Supplier--> [Inventory]
[Order]   --Customer/Supplier--> [Payment]
[Drop]    --Customer/Supplier--> [Inventory]
[Order]   <--ACL-- [Product]   (via ProductClient RestClient)
[Customer] ..Published Language.. [Order]   (customerPublicId by value)
```

## Aggregate Boundaries

```
Product Aggregate          Brand Aggregate
  Product (root)             Brand (root)
  ├── ProductVariant
  ├── ProductImage
  └── ProductTranslation

Orders Aggregate           Payment Aggregate
  Orders (root)              Payment (root)
  ├── OrderItem              └── PaymentEvent
  └── OrderStatusHistory

DropEvent Aggregate        Inventory Aggregate
  DropEvent (root)           Inventory (root)
  ├── DropProduct            └── InventoryEvent
  └── DropStatusHistory

Customer Aggregate
  Customer (root)
  └── CustomerAddress
```

## Domain Events

| Event | Publisher | Consumer | Payload |
|-------|----------|----------|---------|
| OrderCreatedEvent | Order | Payment, Inventory | orderId, items[], totalAmount |
| OrderCancelledEvent | Order | Inventory | orderId, reason |
| PaymentCompletedEvent | Payment | Order | paymentPublicId, orderPublicId, amount |
| PaymentFailedEvent | Payment | Order, Inventory | paymentPublicId, orderPublicId, reason |
| StockReservedEvent | Inventory | Order | productVariantId, quantity, orderPublicId |
| StockReleasedEvent | Inventory | — | productVariantId, quantity, reason |
| DropStatusChangedEvent | Drop | Product | dropEventPublicId, previousStatus, newStatus |
| ProductCreatedEvent | Product | Search | productPublicId, brandPublicId, name |
| CustomerRegisteredEvent | Customer | — | customerPublicId, email |

## Value Objects

| Value Object | Fields | Used In |
|-------------|--------|---------|
| Money | BigDecimal amount, Currency currency | Product, Order, Payment |
| Measurement | BigDecimal chest/shoulder/length/sleeve (cm) | ProductVariant |
| Address | street, city, state, zipCode, country | CustomerAddress |
| DropSchedule | announceAt, openAt, closeAt | DropEvent |
| IdempotencyKey | String value | Orders, Payment |

## Cross-Aggregate Reference Rules

All inter-aggregate references use ID only (never object references):
- Product → Brand: `brandId: Long`
- OrderItem → ProductVariant: `productVariantId: Long` + price snapshot
- Orders → Customer: `customerId: Long`
- Payment → Orders: `orderPublicId: String`
- DropProduct → ProductVariant: `productVariantId: Long`
- Inventory → ProductVariant: `productVariantId: Long`

## Concurrency Strategy

- `Inventory`: `@Version` optimistic locking (drop spike concurrent reservations)
- `DropProduct`: `@Version` optimistic locking (concurrent sold quantity updates)
- Retry: exponential backoff, max 3 attempts on OptimisticLockException

## Distillation

| Priority | Domain | Investment | Rationale |
|----------|--------|-----------|-----------|
| 1 | Drop Commerce | Highest | Platform differentiator; timed limited-release flow |
| 2 | Product Catalog | High | Heritage wear classification, cm measurements, trilingual |
| 3 | Order | High | Drop spike ordering stability |
| 4 | Inventory | Medium | Drop/Order consistency |
| 5 | Payment | Medium | Standard flow + multi-currency |
| 6 | Customer | Low | Standard; replaceable with external auth |
