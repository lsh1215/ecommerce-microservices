# k6 Load Tests

Load testing scripts for the ecommerce MSA platform using [k6](https://k6.io/).

## Install k6

```bash
brew install k6
```

## Scripts

### order-flow.js (Smoke Test)

Basic end-to-end order flow: list products, view detail, create order. Runs 1 VU for 10 seconds.

```bash
k6 run k6/scripts/order-flow.js
```

Override API endpoints:

```bash
k6 run -e PRODUCT_API=http://localhost:8081 -e ORDER_API=http://localhost:8082 k6/scripts/order-flow.js
```

### spike-test.js (Spike Test)

Simulates a drop event traffic spike: ramps from 0 to 100 concurrent users in 30 seconds, then ramps down.

```bash
k6 run k6/scripts/spike-test.js
```

### cascading-failure.js (Cascading Failure Test)

Tests Order service behavior under sustained load when a dependent service (e.g., Product) is down. Stop the Product service before running.

```bash
# 1. Stop Product service
# 2. Run the test
k6 run k6/scripts/cascading-failure.js
```

## Thresholds

| Script              | p(95) Latency | Error Rate |
|---------------------|---------------|------------|
| order-flow          | < 2s          | < 10%      |
| spike-test          | < 3s          | < 15%      |
| cascading-failure   | < 5s          | < 50%      |

## Output

Export results to JSON for analysis:

```bash
k6 run --out json=k6-results/result.json k6/scripts/order-flow.js
```

The `k6-results/` directory is gitignored.
