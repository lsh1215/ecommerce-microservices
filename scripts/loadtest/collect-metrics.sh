#!/usr/bin/env bash
# 측정 구간의 서버측 지표를 Prometheus에서 직접 회수한다.
#
# 스크린샷은 사람이 눈으로 봐야 하고 시간 범위를 잘못 잡으면 값이 희석되지만,
# promQL 결과는 구간을 명시적으로 지정하므로 재현·검증이 가능하다.
#
#   collect-metrics.sh <label> <outdir> <window>     예: collect-metrics.sh s1-atomic ./out 5m
#
# ★ 가장 중요한 항목은 CPU throttling 이다. 이 값이 유의미하면 그 런은 폐기한다
#   (코드가 아니라 CPU limit을 측정한 것이므로). docs/loadtest/README.md 규칙 2.

set -euo pipefail

LABEL="${1:?label required}"
OUTDIR="${2:?outdir required}"
WINDOW="${3:-5m}"
NS_MON="${NS_MON:-monitoring}"
PORT="${PROM_PORT:-19090}"

mkdir -p "$OUTDIR"
OUT="${OUTDIR}/${LABEL}.metrics.json"

kubectl -n "$NS_MON" port-forward svc/prometheus "${PORT}:9090" >/dev/null 2>&1 &
PF_PID=$!
trap 'kill "$PF_PID" 2>/dev/null || true' EXIT

for _ in $(seq 1 30); do
  curl -sf "http://localhost:${PORT}/-/ready" >/dev/null 2>&1 && break
  sleep 1
done

q() { # q <name> <promql>
  local name="$1" expr="$2"
  local res
  res=$(curl -sG "http://localhost:${PORT}/api/v1/query" --data-urlencode "query=${expr}" 2>/dev/null || echo '{}')
  jq -n --arg n "$name" --arg e "$expr" --argjson r "${res:-{\}}" '{name:$n, query:$e, result:$r}'
}

{
  echo '{'
  echo "\"label\": \"${LABEL}\", \"window\": \"${WINDOW}\", \"capturedAt\": \"$(date -u +%FT%TZ)\","
  echo '"metrics": ['
  q "cpu_throttled_ratio_order" \
    "sum(rate(container_cpu_cfs_throttled_periods_total{namespace=\"ecommerce\",pod=~\"service-order.*\"}[${WINDOW}])) / clamp_min(sum(rate(container_cpu_cfs_periods_total{namespace=\"ecommerce\",pod=~\"service-order.*\"}[${WINDOW}])),0.001)"
  echo ','
  q "cpu_throttled_ratio_product" \
    "sum(rate(container_cpu_cfs_throttled_periods_total{namespace=\"ecommerce\",pod=~\"service-product.*\"}[${WINDOW}])) / clamp_min(sum(rate(container_cpu_cfs_periods_total{namespace=\"ecommerce\",pod=~\"service-product.*\"}[${WINDOW}])),0.001)"
  echo ','
  q "cpu_usage_cores" \
    "sum by (pod) (rate(container_cpu_usage_seconds_total{namespace=\"ecommerce\",pod=~\"service-(order|product).*\"}[${WINDOW}]))"
  echo ','
  # ★ 게이지를 그냥 조회하면 '부하가 끝난 뒤'의 값을 읽는다. 그 시점엔 대기가 이미 빠져 항상 0이다.
  # R2~R7 에서 "pending 0" 이라고 판정한 것이 이 오류였다. 반드시 구간 최댓값으로 본다.
  q "hikari_pending_max" \
    "max by (app) (max_over_time(hikaricp_connections_pending{app=~\"service-.*\"}[${WINDOW}]))"
  echo ','
  q "hikari_active_max" \
    "max by (app) (max_over_time(hikaricp_connections_active{app=~\"service-.*\"}[${WINDOW}]))"
  echo ','
  # 풀 고갈의 직접 증거. 스레드가 커넥션을 받기까지 기다린 시간이 곧 사용자 지연이 된다.
  q "hikari_acquire_wait_max_seconds" \
    "max by (app) (max_over_time(hikaricp_connections_acquire_seconds_max{app=~\"service-.*\"}[${WINDOW}]))"
  echo ','
  q "http_5xx_ratio_order" \
    "sum(rate(http_server_requests_seconds_count{app=\"service-order\",status=~\"5..\"}[${WINDOW}])) / clamp_min(sum(rate(http_server_requests_seconds_count{app=\"service-order\"}[${WINDOW}])),0.001)"
  echo ','
  q "server_span_p95_order" \
    "histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{app=\"service-order\",uri=\"/api/orders\"}[${WINDOW}])))"
  echo ','
  q "server_span_p99_order" \
    "histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{app=\"service-order\",uri=\"/api/orders\"}[${WINDOW}])))"
  echo ','
  q "server_rps_order" \
    "sum(rate(http_server_requests_seconds_count{app=\"service-order\",uri=\"/api/orders\"}[${WINDOW}]))"
  echo ']'
  echo '}'
} > "$OUT"

echo "[metrics] → $OUT"
jq -r '.metrics[] | select(.name|startswith("cpu_throttled")) |
  "  \(.name) = \(.result.data.result[0].value[1] // "n/a")"' "$OUT" 2>/dev/null || true
echo "  ※ throttled_ratio 가 0에 가깝지 않으면 이 런은 폐기 대상이다 (규칙 2)"
