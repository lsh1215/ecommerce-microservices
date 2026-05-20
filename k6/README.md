# k6 Load Testing

Load testing scenarios for the ecommerce microservices platform.

## Setup

```bash
brew install k6
```

All scripts accept endpoint overrides through environment variables.

```bash
k6 run -e PRODUCT_API=http://localhost:8081 -e ORDER_API=http://localhost:8082 k6/scripts/order-flow.js
```

## Core Scripts

### `k6/scripts/order-flow.js`

Small end-to-end order flow: product list, product detail, and order creation.

```bash
k6 run k6/scripts/order-flow.js
```

### `k6/scripts/hot-row-rampup.js`

Open-model breakpoint test against one inventory row. Use it when measuring lock contention or comparing stock reservation implementations.

```bash
k6 run -e ORDER_API=http://localhost:8082 -e VARIANT_ID=1 k6/scripts/hot-row-rampup.js
```

### `k6/scripts/spike-test.js`

Short VU spike for checking how the order path behaves during sudden traffic changes.

```bash
k6 run k6/scripts/spike-test.js
```

### `k6/scripts/cascading-failure.js`

Sustained order traffic for dependent-service failure tests.

```bash
k6 run k6/scripts/cascading-failure.js
```

### `k6/scripts/phase4-slow-product.js`

Compares Order service behavior while Product is slow. Useful for validating circuit-breaker behavior and fallback latency.

```bash
k6 run k6/scripts/phase4-slow-product.js
```

## Scenario Set

The `k6/scenarios` directory contains broader service-level checks:

- `smoke-test.js`: core endpoint smoke check.
- `load-test.js`: normal traffic mix.
- `stress-test.js`: coarse VU-based degradation search.

## Output

Export local results as JSON when needed:

```bash
k6 run --out json=k6-results/result.json k6/scripts/order-flow.js
```

Keep capacity conclusions based on success metrics such as `orders_created_2xx/s` or database commit deltas. `http_reqs` includes failed responses and should not be treated as successful throughput.
