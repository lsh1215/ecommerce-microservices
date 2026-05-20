# Stock Reservation Invariants

## Reserve

Product stock reservation is guarded by a single database update:

```sql
UPDATE product_variant
SET stock_quantity = stock_quantity - :qty
WHERE id = :id
  AND stock_quantity >= :qty
```

This makes the stock check and decrement one atomic database operation. Under concurrent
requests, only transactions that observe enough remaining stock can update the row.
When the update count is zero, the service reloads the variant and returns
`INSUFFICIENT_STOCK` instead of applying any in-memory decrement.

Required invariant:

- successful reservations are bounded by the available stock;
- failed reservations do not change stock;
- final stock is never negative.

## Release

Release currently increments stock by quantity:

```sql
UPDATE product_variant
SET stock_quantity = stock_quantity + :qty
WHERE id = :id
```

This operation is atomic, but it is not business-idempotent by itself. A duplicate release
can inflate stock unless the caller or a reservation identity layer prevents the same
reservation from being released more than once.

Required invariant for future reservation-identity work:

- each successful reservation must have a stable reservation key;
- release must transition that reservation from `RESERVED` to `RELEASED` once;
- duplicate release attempts must be no-ops.
