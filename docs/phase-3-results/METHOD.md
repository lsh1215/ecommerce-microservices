# Phase 3 Evidence Method — Idempotent Consumer

## Goal

Prove that **without** Phase 3's idempotency guards, concurrent Kafka consumers can create duplicate `payment` rows for a single `order.created` event — and that **with** the guards enabled, exactly one `payment` row per order is produced even under that concurrency.

The previous "5 events manually injected into a single consumer" experiment did not prove this. A single Kafka consumer processes messages in order inside a single group, and the business-level guard (`existsByOrderIdAndStatus`) catches duplicates even with no `processed_event` table — so a "before" result of "1 payment per 5 injections" is produced by the default business guard, not by idempotency. This harness closes that gap by introducing real concurrent delivery.

## Scenario

Two `service-payment` instances run simultaneously with distinct Kafka consumer groups:

| Instance | Profile | HTTP port | Kafka group-id |
|----------|---------|-----------|----------------|
| A        | `local,phase3-groupA` | `:8083` | `payment-group-a` |
| B        | `local,phase3-groupB` | `:8183` | `payment-group-b` |

Kafka delivers each published message to *every* consumer group independently, so both instances receive every `order.created` event.  Race is real — not the artificial "5 messages, 1 consumer" case.

## Toggles

Two properties gate the two Phase 3 guarantees:

| Property | File | Off behavior |
|----------|------|--------------|
| `application.idempotency.enabled` | `backend-v2/common/src/main/java/com/ecommerce/common/idempotency/IdempotentEventHandler.java` | Skip both the `existsByEventId` check and the `processed_event` insert. Processor runs on every delivery. |
| `application.business-idempotency-guard.enabled` | `backend-v2/service-payment/src/main/java/com/ecommerce/payment/application/service/PaymentService.java#processFromEvent` | Skip the `existsByOrderIdAndStatus(orderId, COMPLETED)` business check. Every `processFromEvent` call creates a new `payment` row. |

Both default to `true` (Phase 3 production behavior).  The harness flips both in lockstep via `--guards on|off`.

The two guards work in tandem:
- `processed_event.event_id` is `UNIQUE`, so with `idempotency.enabled=true` the DB rejects the second commit via `DataIntegrityViolationException`, which triggers `@Transactional` rollback on the losing instance — *including the `payment.save(...)` that ran inside the same transaction*.  Exactly-once.
- With `idempotency.enabled=true` but `business-idempotency-guard.enabled=false`, behavior is still exactly-once (processed_event carries the final guarantee).
- With `idempotency.enabled=false` but `business-idempotency-guard.enabled=true`, the business guard provides weaker "at most one COMPLETED" semantics but races can produce two PENDING rows if both instances pass the check before either commits COMPLETED.
- With both `false`, no guard exists — both instances commit `payment` rows.

The harness disables both simultaneously (`--guards off`) to produce an unambiguous duplicate-rich "before" signal, and enables both (`--guards on`) to validate the full Phase 3 model.

## Reproduction

### Prerequisites
1. Docker compose stack running: `docker compose -f infra/docker-compose.yml up -d mysql kafka`
2. Order service running on `:8082`: `cd backend-v2 && ./gradlew :service-order:bootRun --args='--spring.profiles.active=local' &`
3. CLI tools available: `jq`, `mysql`, `curl`.

### Capture "before" evidence (duplicates expected)

```bash
./scripts/phase3-multi-consumer-test.sh --guards off --orders 50
```

Output is tee'd to `docs/phase-3-results/evidence/multi-consumer-off-<TS>.txt`. Exit code 0 means duplicates were detected as expected; exit 1 means the harness failed to reproduce the race.

Expected result: at least one `order_id` has `COUNT(*) > 1` in the `payment` table. Typical result is `> 1.5×` of posted orders in total `payment` rows (two groups, two writes per order).

### Capture "after" evidence (no duplicates)

```bash
./scripts/phase3-multi-consumer-test.sh --guards on --orders 50
```

Output is tee'd to `docs/phase-3-results/evidence/multi-consumer-on-<TS>.txt`. Exit code 0 means zero duplicates; exit 1 means idempotency failed.

Expected result: `COUNT(*) = 1` for every `order_id`; total `payment` rows = number of orders posted.

### Evidence file location

All evidence artifacts live **in the main repo** at `docs/phase-3-results/evidence/`.  The harness is runnable from any worktree (it resolves the repo root via `$(dirname "${BASH_SOURCE[0]}")/..`), but the canonical evidence files are committed to `main` via the feature branch `test/phase3-multi-consumer-evidence`.

## Unit-test coverage for the toggles

The runtime toggles are also proven in isolation by JUnit:

- `backend-v2/service-order/src/test/java/com/ecommerce/order/common/idempotency/IdempotentEventHandlerTest.java` — new cases `tryProcess_idempotencyDisabled_alwaysRunsProcessorAndSkipsRecord` and `tryProcess_idempotencyDisabled_allowsMultipleInvocationsForSameEventId`.
- `backend-v2/service-payment/src/test/java/com/ecommerce/payment/application/service/PaymentServiceTest.java` — new cases `processFromEvent_guardDisabled_skipsCompletedCheckAndAlwaysProcesses` and `processFromEvent_guardDisabled_allowsDuplicatePaymentCreation`.

These exercise the toggle logic without needing Kafka, so CI can enforce the behavioral contract of the toggle even though the duplicate-detection harness itself is local-only.

## Interpretation caveats

1. **Duplicate rate with `--guards off`**: typically 50–100% depending on Kafka partition assignment and consumer lag. 30–100% is the expected window; below 30% suggests the race timing shifted (e.g., one instance was slow to attach to Kafka). The harness waits for both groups Stable before posting orders to minimize this.
2. **Consumer group rebalance**: initial join of each group adds ~1–5s. The harness waits up to 90s for both groups to be describable, then sleeps an additional 5s as safety margin.
3. **Order service**: uses a single consumer group (`service-order`) for `payment.completed`/`payment.failed`. This harness does not test Order's idempotency; it only tests the duplicate-generation and detection at the Payment side.
4. **`@Transactional` rollback in idempotency-enabled mode**: when `processed_event` insert fails, the `payment.save(...)` in the same transaction is rolled back. This is critical to the exactly-once guarantee and is verified indirectly by the harness observing `COUNT(*) = 1`.
