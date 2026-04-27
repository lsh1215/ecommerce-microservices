#!/usr/bin/env bash
# Per-leg measurement orchestrator.
#
# Usage: measure-unit.sh <unit> <leg>
#   unit: 01 | 02 | 03 | 04
#   leg: problem | solution
#
# Pipeline: load spec → deploy images → set chaos env → wait for rollout
#           → warmup k6 + stabilizer → record measurement window start
#           → run measurement k6 → record window end → capture dashboards
#           → invoke critic (script + LLM agent) → update manifest
#
# Exit 0 = leg captured-and-passed. Exit 1 = captured-but-failed.

set -euo pipefail

UNIT=$1
LEG=$2
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SPEC_FILE="$ROOT/scripts/unit-spec/${UNIT}-*.env"
SPEC_FILE=$(ls $SPEC_FILE 2>/dev/null | head -1)
[ -f "$SPEC_FILE" ] || { echo "ERROR: spec not found for unit $UNIT"; exit 2; }

# Branch precondition (Critic critical #3)
BRANCH=$(git -C "$ROOT" rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" != "fix/observability-exporters-and-dashboards" ]; then
  echo "ERROR: must run from fix/observability-exporters-and-dashboards branch (current: $BRANCH)"
  exit 2
fi

# shellcheck disable=SC1090
source "$SPEC_FILE"
echo "[measure] unit=$UNIT leg=$LEG spec=$SPEC_FILE"

ZONE=asia-northeast3-a
VM=ecommerce-k3s
EXT=http://34.64.219.137
EVIDENCE_DIR_ABS="$ROOT/$EVIDENCE_DIR/$LEG"
mkdir -p "$EVIDENCE_DIR_ABS/dashboards"

# Resolve per-leg vars
case "$LEG" in
  problem)
    ORDER_IMAGE=$PROBLEM_ORDER_IMAGE
    PAYMENT_IMAGE=$PROBLEM_PAYMENT_IMAGE
    PAYMENT_ENV=$PROBLEM_PAYMENT_ENV
    PRODUCT_ENV=$PROBLEM_PRODUCT_ENV
    ;;
  solution)
    ORDER_IMAGE=$SOLUTION_ORDER_IMAGE
    PAYMENT_IMAGE=$SOLUTION_PAYMENT_IMAGE
    PAYMENT_ENV=$SOLUTION_PAYMENT_ENV
    PRODUCT_ENV=$SOLUTION_PRODUCT_ENV
    ;;
  *)
    echo "ERROR: leg must be problem|solution"; exit 2 ;;
esac
TESTID="u${UNIT}-${LEG}"

# Image pre-flight: required tarballs cached on VM (pre-mortem 7.4)
echo "[1/9] verify images present in containerd"
for img in "$ORDER_IMAGE" "$PAYMENT_IMAGE"; do
  tag=$(echo "$img" | cut -d: -f2)
  svc=$(echo "$img" | sed 's|.*/\(service-[a-z-]*\):.*|\1|')
  cached="$HOME/evidence-cache/${svc}_${tag}.tar"
  gcloud compute ssh "$VM" --zone="$ZONE" --command="
    if ! sudo k3s ctr images list 2>&1 | grep -q '$img'; then
      echo '[restore] $img missing — re-importing from ~/evidence-cache/'
      [ -f $cached ] && sudo k3s ctr images import $cached
    fi
  " 2>&1 | tail -2
done

echo "[2/9] deploy images + chaos env"
gcloud compute ssh "$VM" --zone="$ZONE" --command="
  sudo kubectl -n ecommerce set image deploy/service-order service-order=docker.io/$ORDER_IMAGE
  sudo kubectl -n ecommerce set image deploy/service-payment service-payment=docker.io/$PAYMENT_IMAGE
  if [ -n '$PAYMENT_ENV' ]; then
    sudo kubectl -n ecommerce set env deploy/service-payment $PAYMENT_ENV
  fi
  if [ -n '$PRODUCT_ENV' ]; then
    sudo kubectl -n ecommerce set env deploy/service-product $PRODUCT_ENV
  fi
  sudo kubectl -n ecommerce rollout status deploy/service-order --timeout=240s 2>&1 | tail -1
  sudo kubectl -n ecommerce rollout status deploy/service-payment --timeout=240s 2>&1 | tail -1
  sudo kubectl -n ecommerce rollout status deploy/service-product --timeout=240s 2>&1 | tail -1
" 2>&1 | tail -5

echo "[3/9] grace period (10s) for sidecar metrics + readiness"
sleep 10

echo "[4/9] k6 warmup smoke load (VUs=$WARMUP_VUS, $WARMUP_DURATION)"
if [ "$K6_VUS" -gt 0 ]; then
  gcloud compute scp --zone="$ZONE" "$ROOT/scripts/k6-warmup.js" "$VM:/tmp/" 2>&1 | tail -1
  gcloud compute ssh "$VM" --zone="$ZONE" --command="
    WARMUP_VUS=$WARMUP_VUS WARMUP_DURATION=$WARMUP_DURATION ORDER_API=$EXT \
      k6 run --tag testid=warmup-${TESTID} /tmp/k6-warmup.js 2>&1 | tail -3
  " 2>&1 | tail -3
fi

echo "[5/9] stabilizer poll (target=${STABILIZE_TARGET_MS}ms x${STABILIZE_CONSECUTIVE})"
ORDER_API=$EXT bash "$ROOT/scripts/warmup-stabilizer.sh" \
  "$STABILIZE_TARGET_MS" "$STABILIZE_CONSECUTIVE" 60

echo "[6/9] measurement window start"
START_MS=$(python3 -c 'import time; print(int(time.time()*1000))')
echo "  testid=$TESTID start=$START_MS"

# Categorical units (Unit 03) skip k6 measurement
if [ "$K6_VUS" -gt 0 ]; then
  gcloud compute scp --zone="$ZONE" "$ROOT/$K6_SCRIPT" "$VM:/tmp/k6-measure.js" 2>&1 | tail -1
  if [ -n "${CHAOS_KICKER:-}" ]; then
    gcloud compute scp --zone="$ZONE" "$ROOT/$CHAOS_KICKER" "$VM:/tmp/chaos-kicker.sh" 2>&1 | tail -1
    gcloud compute ssh "$VM" --zone="$ZONE" --command='chmod +x /tmp/chaos-kicker.sh; /tmp/chaos-kicker.sh &' 2>&1 | tail -1
  fi
  gcloud compute ssh "$VM" --zone="$ZONE" --command="
    PROM=\$(sudo kubectl -n monitoring get svc prometheus -o jsonpath='{.spec.clusterIP}')
    ORDER_API=$EXT PRODUCT_API=$EXT \
    K6_VUS=$K6_VUS K6_DURATION=$K6_DURATION \
    K6_PROMETHEUS_RW_SERVER_URL=http://\${PROM}:9090/api/v1/write \
    K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),avg,max,min' \
    k6 run --tag testid=$TESTID \
      --out experimental-prometheus-rw \
      /tmp/k6-measure.js 2>&1 | tail -25
  " 2>&1 | tee "$EVIDENCE_DIR_ABS/k6-v3.txt" | tail -20
fi

END_MS=$(python3 -c 'import time; print(int(time.time()*1000))')
echo "[7/9] measurement window end=$END_MS (duration=$((END_MS - START_MS))ms)"

# Window trim (Unit 04 CB sliding-window grace)
EFFECTIVE_FROM=$((START_MS + ${WINDOW_TRIM_MS:-0}))

# Record state for critic + capture
cat > "$EVIDENCE_DIR_ABS/state.json" <<JSON
{
  "unit": "$UNIT",
  "leg": "$LEG",
  "testid": "$TESTID",
  "raw_start_ms": $START_MS,
  "effective_from_ms": $EFFECTIVE_FROM,
  "to_ms": $END_MS,
  "window_trim_ms": ${WINDOW_TRIM_MS:-0},
  "k6_script": "$K6_SCRIPT",
  "k6_vus": $K6_VUS,
  "warmup_vus": ${WARMUP_VUS:-0},
  "spec_file": "$SPEC_FILE",
  "captured_at": "$(date -u +%FT%TZ)"
}
JSON
echo "  state: $EVIDENCE_DIR_ABS/state.json"

echo "[8/9] capture dashboards (var-testid=$TESTID)"
FROM=$EFFECTIVE_FROM TO=$END_MS \
WAIT_MS=20000 \
OUT_DIR="$EVIDENCE_DIR_ABS/dashboards" \
DASHBOARDS=$CAPTURE_DASHBOARDS \
VAR_TESTID=$TESTID \
node "$ROOT/scripts/visual-audit/capture-dashboards.mjs" 2>&1 | tail -3

echo "[9/9] critic verification (script + LLM agent)"
python3 "$ROOT/scripts/critic-evidence.py" "$EVIDENCE_DIR_ABS"
SCRIPT_EXIT=$?

echo "[done] unit=$UNIT leg=$LEG  script-critic=$SCRIPT_EXIT"
echo "       LLM critic delegation: invoke `oh-my-claudecode:critic` with $EVIDENCE_DIR_ABS"

exit $SCRIPT_EXIT
