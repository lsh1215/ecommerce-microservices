#!/usr/bin/env bash
#
# phase3-multi-consumer-test.sh
# ------------------------------------------------------------
# Phase 3 — Idempotent Consumer evidence harness.
#
# Launches two Payment service instances with distinct Kafka consumer
# groups (payment-group-a on :8083, payment-group-b on :8183) against a
# single Order service (:8082).  Both instances receive every
# order.created event (Kafka delivers to each consumer group
# independently), so the race is real — not a "single consumer serial
# dedup" false positive.
#
# Usage:
#   scripts/phase3-multi-consumer-test.sh --guards on|off [--orders N]
#
#   --guards off  Disables both toggles — simulates the pre-Phase-3
#                 codebase:
#                   application.idempotency.enabled=false
#                   application.business-idempotency-guard.enabled=false
#                 Expected: duplicate payment rows per order_id.
#
#   --guards on   Enables both guards (Phase 3 default).
#                 Expected: exactly 1 payment per order_id across all
#                 order events.
#
# Prerequisites:
#   - docker compose stack running (MySQL :3307, Kafka :9092).
#   - Order service running on :8082 (default `local` profile).
#   - `jq`, `mysql` CLI, Kafka CLI inside the kafka container.
# ------------------------------------------------------------
set -euo pipefail

# ---- defaults ----
GUARDS=""
ORDERS=50
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EVIDENCE_DIR="${REPO_ROOT}/docs/phase-3-results/evidence"
LOG_DIR="${REPO_ROOT}/build/phase3-logs"
mkdir -p "${EVIDENCE_DIR}" "${LOG_DIR}"

# ---- args ----
while [[ $# -gt 0 ]]; do
  case "$1" in
    --guards) GUARDS="$2"; shift 2 ;;
    --orders) ORDERS="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

if [[ "${GUARDS}" != "on" && "${GUARDS}" != "off" ]]; then
  echo "Usage: $0 --guards on|off [--orders N]" >&2
  exit 2
fi

if [[ "${GUARDS}" == "off" ]]; then
  IDEMPOTENCY_ENABLED=false
  BUSINESS_GUARD_ENABLED=false
  EXPECTATION="duplicates expected (before-state)"
else
  IDEMPOTENCY_ENABLED=true
  BUSINESS_GUARD_ENABLED=true
  EXPECTATION="exactly one payment per order (after-state)"
fi

# ---- evidence header ----
TS="$(date +%Y%m%d-%H%M%S)"
OUT_FILE="${EVIDENCE_DIR}/multi-consumer-${GUARDS}-${TS}.txt"
exec > >(tee -a "${OUT_FILE}") 2>&1

echo "============================================================"
echo "Phase 3 multi-consumer duplicate-detection harness"
echo "============================================================"
echo "Run timestamp:   $(date -u +%FT%TZ)"
echo "Repo:            ${REPO_ROOT}"
echo "Git HEAD:        $(git -C "${REPO_ROOT}" rev-parse HEAD)"
echo "Git status:      $(git -C "${REPO_ROOT}" status --porcelain | wc -l | tr -d ' ') uncommitted changes"
echo "Branch:          $(git -C "${REPO_ROOT}" rev-parse --abbrev-ref HEAD)"
echo "--guards:        ${GUARDS}"
echo "Orders to post:  ${ORDERS}"
echo "Expectation:     ${EXPECTATION}"
echo "Toggles:         application.idempotency.enabled=${IDEMPOTENCY_ENABLED}"
echo "                 application.business-idempotency-guard.enabled=${BUSINESS_GUARD_ENABLED}"
echo "------------------------------------------------------------"
echo "docker compose ps:"
docker compose -f "${REPO_ROOT}/infra/docker-compose.yml" ps 2>/dev/null || docker ps --format 'table {{.Names}}\t{{.Status}}'
echo "------------------------------------------------------------"

# ---- reset DB state ----
MYSQL_EXEC() {
  docker exec -i ecommerce-mysql mysql -uroot -p1234 "$@"
}
echo "[step 1] Resetting processed_event and payment tables"
MYSQL_EXEC ecommerce_payment <<'SQL'
DELETE FROM processed_event;
DELETE FROM payment;
SQL
echo "  processed_event + payment cleared"

# ---- build jar ----
echo "[step 2] Building service-payment bootJar (skipping tests)"
(cd "${REPO_ROOT}/backend-v2" && ./gradlew :service-payment:bootJar -x test -q)
PAYMENT_JAR="$(ls "${REPO_ROOT}/backend-v2/service-payment/build/libs/"*.jar | head -n1)"
echo "  jar: ${PAYMENT_JAR}"

# ---- start two instances ----
COMMON_JVM_ARGS=(
  "-Dapplication.idempotency.enabled=${IDEMPOTENCY_ENABLED}"
  "-Dapplication.business-idempotency-guard.enabled=${BUSINESS_GUARD_ENABLED}"
)

echo "[step 3] Launching Payment instance A (profile phase3-groupA, port 8083)"
java "${COMMON_JVM_ARGS[@]}" -jar "${PAYMENT_JAR}" \
  --spring.profiles.active=local,phase3-groupA \
  > "${LOG_DIR}/pay-a.log" 2>&1 &
PID_A=$!
echo "  PID_A=${PID_A} -> ${LOG_DIR}/pay-a.log"

echo "[step 4] Launching Payment instance B (profile phase3-groupB, port 8183)"
java "${COMMON_JVM_ARGS[@]}" -jar "${PAYMENT_JAR}" \
  --spring.profiles.active=local,phase3-groupB \
  > "${LOG_DIR}/pay-b.log" 2>&1 &
PID_B=$!
echo "  PID_B=${PID_B} -> ${LOG_DIR}/pay-b.log"

cleanup() {
  echo "[cleanup] Stopping Payment instances"
  kill "${PID_A}" "${PID_B}" 2>/dev/null || true
  wait "${PID_A}" 2>/dev/null || true
  wait "${PID_B}" 2>/dev/null || true
}
trap cleanup EXIT

# ---- wait for both consumer groups Stable ----
echo "[step 5] Waiting for consumer groups Stable"
wait_group_stable() {
  local group="$1"
  local deadline=$((SECONDS + 90))
  while (( SECONDS < deadline )); do
    if docker exec ecommerce-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
        --bootstrap-server localhost:9092 --describe --group "${group}" 2>/dev/null \
        | grep -q "Stable\|${group}.*[0-9]"; then
      # Tolerate both "Stable" state and any assignment row.
      # First join may not print "Stable" literally on some kafka versions.
      echo "  ${group}: ready"
      return 0
    fi
    sleep 2
  done
  echo "  ${group}: timeout waiting for stable" >&2
  return 1
}
wait_group_stable payment-group-a
wait_group_stable payment-group-b
sleep 5  # safety margin so both instances have attached their KafkaListeners

# ---- post orders ----
echo "[step 6] Posting ${ORDERS} orders to Order service :8082"
ORDER_IDS=()
for i in $(seq 1 "${ORDERS}"); do
  RESP=$(curl -sS -X POST http://localhost:8082/api/orders \
    -H 'Content-Type: application/json' \
    -d "{\"customerId\":1,\"items\":[{\"productVariantId\":1,\"quantity\":1}]}" \
    || true)
  ORDER_ID=$(echo "${RESP}" | jq -r '.data.id // empty' 2>/dev/null || true)
  if [[ -z "${ORDER_ID}" ]]; then
    echo "  [${i}/${ORDERS}] failed: ${RESP}" >&2
  else
    ORDER_IDS+=("${ORDER_ID}")
  fi
done
echo "  posted ${#ORDER_IDS[@]} orders"

# ---- wait for payments to settle ----
echo "[step 7] Waiting 15s for payments to settle"
sleep 15

# ---- count duplicates ----
echo "[step 8] Querying payment duplicates per order_id"
DUPES_SQL="SELECT order_id, COUNT(*) AS payment_count FROM payment GROUP BY order_id HAVING COUNT(*) > 1;"
DUPES_OUT=$(MYSQL_EXEC ecommerce_payment -e "${DUPES_SQL}" || true)
echo "${DUPES_OUT}"

TOTAL_SQL="SELECT COUNT(*) AS total_payments, COUNT(DISTINCT order_id) AS distinct_orders FROM payment;"
TOTAL_OUT=$(MYSQL_EXEC ecommerce_payment -e "${TOTAL_SQL}" || true)
echo "${TOTAL_OUT}"

DUPES_COUNT=$(echo "${DUPES_OUT}" | awk 'NR>1 { c++ } END { print c+0 }')

# ---- verdict ----
echo "------------------------------------------------------------"
echo "VERDICT: ${DUPES_COUNT} order(s) have >1 payment row"
if [[ "${GUARDS}" == "off" ]]; then
  if (( DUPES_COUNT > 0 )); then
    echo "PASS: duplicates detected as expected in before-state (--guards off)"
    exit 0
  else
    echo "FAIL: no duplicates observed in before-state — harness not reproducing the race"
    exit 1
  fi
else
  if (( DUPES_COUNT == 0 )); then
    echo "PASS: no duplicates in after-state (--guards on) — idempotency + business guard effective"
    exit 0
  else
    echo "FAIL: duplicates still present in after-state — idempotency broken"
    exit 1
  fi
fi
