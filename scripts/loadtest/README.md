# Load-test evidence toolkit

Verification gates that run every phase, so fresh evidence is numerically consistent
with its dashboards and usable by both the blog posts and the portfolio.

## `verify-evidence.py` — numeric-integrity gate (plan R6)

Canonical numbers come from the k6 `--summary-export` JSON; the dashboard PNG is read by
vision only for window / no-data / agreement. This tool reconciles them and blocks on the
gist-class defects.

```sh
python3 scripts/loadtest/verify-evidence.py \
  --k6 docs/evidence/latest/<phase>/<case>/logs/measure-k6-summary.json \
  --claim docs/evidence/latest/<phase>/<case>/claim.json \
  --dashboard docs/evidence/latest/<phase>/<case>/audits/dashboard-audit/dashboard-audit.json \
  --env docs/evidence/latest/<phase>/<case>/raw/env.json \
  --baseline-env docs/evidence/latest/_baseline/env.json \
  --tolerance 0.05
python3 scripts/loadtest/verify-evidence.py --selftest   # unit self-check
```

Blocking checks: `avg <= peak` throughput, both `p95` and `p99` present, `error_rate <=
claim.max_error_rate`, `p95 <= claim.slo_p95_ms`, and every dashboard-audit value within
±tolerance of the k6 value. Exit 0 = clean, 1 = blocking, 2 = usage/parse.

## `capture-env.py` — hardware baseline gate

Numeric integrity is not enough. Two runs can both be internally consistent and still be
uncomparable because the hardware moved underneath them. That is exactly what happened on
2026-08-01: `service-product` went from a 2 vCPU node to an 8 vCPU node between phases and
nothing recorded it, so three blog posts no longer share a baseline with the four before
them (`docs/observability/loadtest-baseline-audit.md`).

`capture-env.py` writes `<run>/raw/env.json` — node-pool machine types, per-workload
replicas / CPU+memory requests / limits / nodeSelector, QoS class, and a stable
`fingerprint` over exactly those fields. `run-k6-job.sh` calls it automatically before the
load starts, so every run carries the spec it was measured on.

`verify-evidence.py` then **BLOCKS** on:

- baseline drift — `--env` fingerprint differs from `--baseline-env` (with a per-field diff).
  Pass `--allow-env-change` only when the change is deliberate; doing so means every earlier
  run on the old baseline is now due for re-measurement.
- zero nodes — the run measured nothing.
- total CPU requests exceeding cluster allocatable — nothing could have scheduled.

and WARNs on any workload that is not Guaranteed QoS, because a `request != limit` pod's CPU
share depends on its neighbours and the run is not reproducible.

**Never change a spec with `kubectl` alone.** Spec changes belong in `k8s/` as their own
commit, with the measurement that justifies them in the message. The live cluster carried
`mysql-product 1/6`, `mysql-order 2/6` and a 250Gi order PVC that existed in no manifest.

## Per-phase evidence protocol (resumable atomic steps)

1. `quota-preflight` — `gcloud compute regions describe <region>`; record CPUS ceiling +
   itemized per-phase vCPU ledger (incl. the ephemeral same-zone k6 VM). Do not provision
   until footprint <= ceiling.
2. `provision` — run `provision-cluster.sh` (it re-checks quota itself); `deploy` the phase
   worktree image; record deployed image digest. Never hand-patch specs onto a live cluster.
3. `seed-reset` — deterministic reset before every before/after and every 3× repeat: stock
   restore + reset order/processed_event/payment/outbox + Kafka topics + consumer-group offsets;
   snapshot pre-run counts.
4. `warm-up` — dedicated k6 warm-up run (JIT + HikariCP + Kafka + InnoDB buffer pool). Record
   each SUT pod start-time/restartCount. Discard the warm-up window.
5. `measure` — write `measure-window.env` (from_ms/to_ms) ATOMICALLY *before* the run; then run
   k6 with `K6_PROMETHEUS_RW_TREND_STATS=avg,p(95),p(99)` so panels render both p95 and p99.
   `run-k6-job.sh` captures `raw/env.json` at this point — that fingerprint, not the plan, is
   what the run is comparable against.
6. `measure-verify` — k6 exit 0; sample >= phase threshold.
7. `capture` dashboards over the exact `measure-window.env` window → `screenshots/*.png`.
8. `dashboard-vision-verify` — read each PNG by vision; assert correct window, no unintended
   No-data/NaN/query-error; write the read values to `audits/dashboard-audit/dashboard-audit.json`.
9. `numeric-gate` — run `verify-evidence.py` with `--env` and `--baseline-env`; must PASS.
   A baseline-drift BLOCK is not a nuisance: it means this run cannot be compared to the
   phases before it, and either the spec goes back or those phases get re-measured.
10. `reconcile` — map each headline number to BOTH the blog before/after paragraph and the
    portfolio slide; record in the evidence↔claim mapping doc.
11. On resume: if any SUT pod restartCount/start-time changed since warm-up, the warm-up is
    INVALID — redo from step 4. Track the 3× repeat index so a run is never overwritten.

## Kafka in-node quorum (plan R1)

`k8s/base/kafka-statefulset.yml` runs 3 KRaft combined brokers (RF3 / minISR2) on one
`e2-standard-2` node. This proves broker-process resilience (kill 1 broker → ISR>=2 → zero
loss), NOT node/zone HA. Label it honestly in every artifact.
