#!/usr/bin/env bash
# Replicates for the points that will actually be published.
# Usage: rep2.sh <variant> <tier> <prefix> <rate> <tag>
set -u
cd "$(cd "$(dirname "$0")/../.." && pwd)"
D=docs/evidence/latest/rev8/knee
PP=$(kubectl get pod -n monitoring -l app=prometheus -o jsonpath='{.items[0].metadata.name}')

settle() {
  for _ in $(seq 1 24); do
    ready=$(kubectl get pods -n ecommerce -l app=service-product --no-headers | grep -c "1/1 *Running")
    pending=$(kubectl exec -n monitoring "$PP" -c prometheus -- wget -qO- \
      'http://localhost:9090/api/v1/query?query=sum(hikaricp_connections_pending)' 2>/dev/null |
      sed -n 's/.*"value":\[[^,]*,"\([0-9.]*\)".*/\1/p')
    running=$(kubectl exec -n ecommerce mysql-product-0 -- sh -c \
      'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME=\"Threads_running\""' 2>/dev/null | tr -d '\r')
    if [ "$ready" = "2" ] && [ "${pending:-9}" = "0" ] && [ "${running:-99}" -le 3 ]; then
      sleep 30; return
    fi
    sleep 15
  done
}

V=$1; T=$2; P=$3; R=$4; TAG=$5
settle
bash scripts/loadtest/seed-tiers.sh >/dev/null 2>&1
OUT=$D/${P}-${R}-${TAG}
rm -rf "$OUT"
mkdir -p "$OUT/logs"
LOG=$OUT/logs/run.log
bash scripts/loadtest/run-k6-job.sh k6/scripts/reserve-tier.js "$OUT" \
  VARIANT="$V" RATE="$R" DURATION=120s PRE_VUS=$((R * 5)) MAX_VUS=$((R * 15)) \
  REPLICA=off ARM="${P}-${R}-${TAG}" TIER="$T" >"$LOG" 2>&1
if [ $? -ne 0 ]; then
  echo "  ${P}@${R} ${TAG}: 무릎 초과 — $(grep -oE 'http_req_failed=[0-9.]+%' "$LOG" | head -1) 실패"
  exit 0
fi
python3 scripts/loadtest/run-metrics.py "$OUT" "${P}@${R} ${TAG}"
