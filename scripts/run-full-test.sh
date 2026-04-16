#!/bin/bash
# Phase 5 — Comprehensive load test runner
# Runs smoke → load → stress sequentially and saves outputs for analysis.
#
# Prerequisites:
#   - 4 services running (ports 8081-8084)
#   - k6 installed (brew install k6)
#   - Seed data loaded (./scripts/seed-data.sh)
#
# Usage:
#   ./scripts/run-full-test.sh [--skip-stress]

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RESULTS_DIR="${PROJECT_ROOT}/docs/phase-5-results"
mkdir -p "$RESULTS_DIR"

SKIP_STRESS=false
if [[ "${1:-}" == "--skip-stress" ]]; then
  SKIP_STRESS=true
fi

strip_color() { sed 's/\x1b\[[0-9;]*m//g'; }

echo "=== E-Commerce MSA Phase 5 Full Test Suite ==="
echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S %Z')"
echo ""

# Quick health check
echo "[0/3] Health check..."
for port in 8081 8082 8083 8084; do
  if ! curl -sf "http://localhost:$port/actuator/health" > /dev/null; then
    echo "  ✗ Service on port $port is NOT responding. Aborting."
    exit 1
  fi
  echo "  ✓ Port $port UP"
done
echo ""

# 1. Smoke test
echo "[1/3] Running smoke test (5 VUs, 30s)..."
k6 run "${PROJECT_ROOT}/k6/scenarios/smoke-test.js" 2>&1 | strip_color | tee "${RESULTS_DIR}/smoke.txt" | tail -10
echo ""

# 2. Load test
echo "[2/3] Running load test (ramp to 50 VUs, total ~4.5min)..."
k6 run "${PROJECT_ROOT}/k6/scenarios/load-test.js" 2>&1 | strip_color | tee "${RESULTS_DIR}/load.txt" | tail -10
echo ""

# 3. Stress test (optional — takes ~5.5min)
if [[ "$SKIP_STRESS" == "true" ]]; then
  echo "[3/3] Stress test SKIPPED (--skip-stress)"
else
  echo "[3/3] Running stress test (ramp to 300 VUs, total ~5.5min)..."
  k6 run "${PROJECT_ROOT}/k6/scenarios/stress-test.js" 2>&1 | strip_color | tee "${RESULTS_DIR}/stress.txt" | tail -10
fi
echo ""

echo "=== Done ==="
echo "Results: ${RESULTS_DIR}/"
ls -la "${RESULTS_DIR}/"
