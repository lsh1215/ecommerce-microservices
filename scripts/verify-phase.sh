#!/usr/bin/env bash
# Post-deploy verification for the LGTM monitoring sweep.
#   ./scripts/verify-phase.sh phaseN
#
# Re-stages the monitoring helpers that live in the ecommerce
# namespace (deleted on every phase teardown), refreshes the
# dashboard pins to whatever IPs the new exporter pods landed on,
# generates traffic, and runs both the API audit and the Playwright
# visual audit. Saves evidence under /tmp/phase-evidence/.

set -euo pipefail

PHASE="${1:-phaseN}"
WORKTREE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ZONE=asia-northeast3-a
VM=ecommerce-k3s
EVID=/tmp/phase-evidence
mkdir -p "${EVID}"

echo "[verify] === ${PHASE} ==="

# 1. Re-apply mysqld-exporter (it lives in the ecommerce namespace and
#    gets nuked each teardown).
gcloud compute scp --zone="${ZONE}" "${WORKTREE}/k8s/base/mysqld-exporter.yml" "${VM}:/tmp/" 2>&1 | tail -1
gcloud compute ssh "${VM}" --zone="${ZONE}" --command='
  sudo kubectl apply -f /tmp/mysqld-exporter.yml
  sudo kubectl -n ecommerce rollout status deploy/mysqld-exporter --timeout=120s
'

# 2. Restart kafka-exporter so it re-resolves any new broker IPs.
gcloud compute ssh "${VM}" --zone="${ZONE}" --command='
  sudo kubectl -n monitoring rollout restart deploy/kafka-exporter
  sudo kubectl -n monitoring rollout status deploy/kafka-exporter --timeout=60s
' >/dev/null

# 3. Wait for fresh metric scrapes before pinning. ~30s gives Alloy two
#    cycles to re-discover targets.
echo "[verify] waiting 30s for fresh scrapes…"
( sleep 30 )

# 4. Refresh dashboard pins (mysql/kafka instance IPs).
cd "${WORKTREE}"
./scripts/fetch-dashboards.sh >/dev/null 2>&1
python3 scripts/patch-community-dashboards.py 2>&1 | tail -6
gcloud compute scp --zone="${ZONE}" \
  k8s/monitoring/dashboards/dashboard-mysql-overview.yml \
  k8s/monitoring/dashboards/dashboard-kafka-exporter-overview.yml \
  k8s/monitoring/dashboards/dashboard-jvm-micrometer.yml \
  "${VM}:/tmp/" 2>&1 | tail -1
gcloud compute ssh "${VM}" --zone="${ZONE}" --command='
  sudo kubectl apply -f /tmp/dashboard-mysql-overview.yml -f /tmp/dashboard-kafka-exporter-overview.yml -f /tmp/dashboard-jvm-micrometer.yml
  sudo kubectl -n monitoring rollout restart deploy/grafana
  sudo kubectl -n monitoring rollout status deploy/grafana --timeout=90s
'

# 5. Generate load: 90 s k6 + parallel HTTP polling against /api/products.
BASE=http://34.64.219.137
PRODUCT_API=$BASE ORDER_API=$BASE PAYMENT_API=$BASE CUSTOMER_API=$BASE \
K6_PROMETHEUS_RW_SERVER_URL=http://34.64.219.137:30090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),min,max,avg" \
k6 run -o experimental-prometheus-rw --tag "testid=${PHASE}" --duration 90s --vus 3 \
  k6/scenarios/smoke-test.js > "${EVID}/${PHASE}-k6.log" 2>&1 &
K6_PID=$!
echo "[verify] k6 pid=${K6_PID}"
( for i in $(seq 1 60); do curl -s -o /dev/null "${BASE}/api/products"; sleep 1; done ) >/dev/null 2>&1 &
TR_PID=$!

# 6. Run audits while load is hot. 30s is enough to flush metrics through
#    OTLP + remote_write without waiting for the full k6 run to finish.
sleep 35
python3 scripts/audit-all-dashboards.py 2>"${EVID}/${PHASE}-audit.err" > "${EVID}/${PHASE}-api-audit.md"
( cd scripts/visual-audit && node audit.mjs ) > "${EVID}/${PHASE}-visual.md" 2>"${EVID}/${PHASE}-visual.err"

# 7. Snapshot the dashboard images.
for f in /tmp/grafana-shots/ecommerce-*.png; do
  cp "$f" "${EVID}/${PHASE}-$(basename "$f" .png | sed 's/^ecommerce-//').png"
done

# 8. Tear down the load generators.
kill ${K6_PID} ${TR_PID} 2>/dev/null || true

echo "[verify] === ${PHASE} done ==="
grep -B1 '^\*\*OK' "${EVID}/${PHASE}-api-audit.md" | head -20
echo
echo '--- visual ---'
grep -E '^OK:' "${EVID}/${PHASE}-visual.md" | head -10
