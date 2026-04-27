# Evidence Re-collection Runbook

The four evidence units below each ship as a standalone branch + worktree
that diverges from `main` by exactly one feature. Re-collection cycles
through Problem → Solution per unit and captures the failure-mode delta
in k6 raw output + Grafana dashboard screenshots.

The harness lives on the `fix/observability-exporters-and-dashboards`
branch (PR #47). It is intentionally not merged to `main` — it depends on
overlay scripts, dashboards, and exporters that belong with the
monitoring stack rather than the application contract.

## Prereqs

```bash
# Make sure you're on the harness branch:
git switch fix/observability-exporters-and-dashboards

# Confirm GCE VM is up:
gcloud compute instances describe ecommerce-k3s \
  --zone=asia-northeast3-a --format='value(status)'   # → RUNNING

# Confirm monitoring stack already running (untouched by deploy-phase):
gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a \
  --command='sudo kubectl -n monitoring get pods'
```

## Worktree layout

| Worktree                                  | Branch                            | Reverted feature                                                                  |
|-------------------------------------------|-----------------------------------|-----------------------------------------------------------------------------------|
| `../ecommerce-microservices-worktrees/01-no-saga`        | `evidence/01-no-saga`        | SAGA + Kafka decoupling (Order calls Payment via sync REST)                         |
| `../ecommerce-microservices-worktrees/02-no-outbox`      | `evidence/02-no-outbox`      | Transactional outbox (publish to Kafka AFTER_COMMIT, no DB-side replay)            |
| `../ecommerce-microservices-worktrees/03-no-idempotency` | `evidence/03-no-idempotency` | `application.idempotency.enabled=false` — duplicate Kafka deliveries reprocess     |
| `../ecommerce-microservices-worktrees/04-no-cb`          | `evidence/04-no-cb`          | Resilience4j `@CircuitBreaker` removed from `ProductCatalogRestClient`              |

`main` is the Solution side for all four.

## Per-unit cycle

```bash
# 1. Deploy Problem build
./scripts/deploy-phase.sh 01-no-saga

# 2. Drive load and capture
mkdir -p docs/evidence/01-cascading-failure/problem
k6 run --out experimental-prometheus-rw \
       --tag scenario=problem \
       k6/scripts/cascading-failure.js \
       2>&1 | tee docs/evidence/01-cascading-failure/problem/k6.txt

# 3. Capture dashboards (replace IDs with the panels for this unit)
./scripts/audit-all-dashboards.py \
  --output docs/evidence/01-cascading-failure/problem/

# 4. Deploy Solution (main)
./scripts/deploy-phase.sh main      # add a `main` worktree alias if missing
                                    # (or rebuild + push images directly)

# 5. Re-run the same scenario into the solution dir
mkdir -p docs/evidence/01-cascading-failure/solution
k6 run --tag scenario=solution k6/scripts/cascading-failure.js \
  2>&1 | tee docs/evidence/01-cascading-failure/solution/k6.txt
./scripts/audit-all-dashboards.py \
  --output docs/evidence/01-cascading-failure/solution/

# 6. Write summary.md comparing the two
$EDITOR docs/evidence/01-cascading-failure/summary.md
```

## Expected metric deltas

| Unit | k6 metric                  | Problem build                                        | Solution (main)                                |
|------|----------------------------|------------------------------------------------------|------------------------------------------------|
| 01   | `http_req_failed`           | ≥ 90 % when service-payment is stopped               | < 5 % (Order returns PENDING immediately)       |
| 01   | order POST p95              | ≥ 5 s (sync RestClient timeout pile-up)              | < 500 ms (Kafka publish only)                   |
| 02   | DB rows ↔ Kafka offsets     | drift ≥ 1 (lost events on broker blip)               | 0 drift, `outbox_event.status` 100 % `PUBLISHED` |
| 03   | Payment rows from 5 dup msg | 5                                                    | 1 (other 4 logged as `중복 이벤트 감지`)         |
| 04   | order POST p95 (slow product) | k6 timeout / thread-pool starvation                | < 1 s — CB OPEN fast-fails further calls        |

## Rollback / final state

After all four units are captured:

```bash
# Final state: main on the cluster
./scripts/deploy-phase.sh main
gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a \
  --command='sudo kubectl -n ecommerce get pods'
```

Leave the VM running.

## What ships where

| Branch                                     | Contents                                           | Disposition                          |
|--------------------------------------------|----------------------------------------------------|--------------------------------------|
| `main`                                      | JWT trust + the four production features          | merged (canonical)                   |
| `evidence/01..04-no-X`                      | one regression each, on top of `main`             | pushed, never merged — pure fixtures |
| `fix/observability-exporters-and-dashboards`| deploy/verify scripts, Alloy/Loki/Tempo/Prom CMs, dashboards, evidence/ docs | stays open as PR #47   |
