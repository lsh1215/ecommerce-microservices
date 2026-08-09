#!/usr/bin/env bash
# k6 시나리오 하나를 in-cluster Job으로 실행하고 결과를 회수한다.
#
#   run-scenario.sh <testid> <script.js> <outdir> [KEY=VAL ...]
#
# 회수물: <outdir>/<testid>.log (전체 stdout)  <outdir>/<testid>.summary.json (k6 summary)

set -euo pipefail

TESTID="${1:?testid required}"
SCRIPT="${2:?script name required (e.g. hot-row-rampup.js)}"
OUTDIR="${3:?output dir required}"
shift 3

NS="${NS:-ecommerce}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEMPLATE="$REPO_ROOT/k8s/loadtest/k6-job.yaml"

mkdir -p "$OUTDIR"

# 시나리오별 env는 ConfigMap으로 주입한다(Job 템플릿이 optional 로 참조).
kubectl delete configmap "k6-env-${TESTID}" -n "$NS" --ignore-not-found >/dev/null 2>&1 || true
if [[ $# -gt 0 ]]; then
  args=()
  for kv in "$@"; do args+=(--from-literal="$kv"); done
  kubectl create configmap "k6-env-${TESTID}" -n "$NS" "${args[@]}" >/dev/null
  echo "[k6] env: $*"
fi

kubectl delete job "k6-${TESTID}" -n "$NS" --ignore-not-found >/dev/null 2>&1 || true

sed -e "s|\${TESTID}|${TESTID}|g" \
    -e "s|\${SCRIPT}|${SCRIPT}|g" \
    -e "s|\${K6_FLAGS}||g" \
    "$TEMPLATE" | kubectl apply -f - >/dev/null

echo "[k6] ${TESTID} 실행 중 (${SCRIPT}) ..."

# k6가 threshold 실패로 non-zero 종료하면 Job은 Failed가 된다. 그래도 로그와 summary는 회수해야
# 하므로 complete/failed 둘 다 종료 조건으로 본다.
deadline=$(( $(date +%s) + 1800 ))
while :; do
  succeeded=$(kubectl get job "k6-${TESTID}" -n "$NS" -o jsonpath='{.status.succeeded}' 2>/dev/null || echo "")
  failed=$(kubectl get job "k6-${TESTID}" -n "$NS" -o jsonpath='{.status.failed}' 2>/dev/null || echo "")
  [[ -n "$succeeded" && "$succeeded" != "0" ]] && { echo "[k6] ${TESTID} 완료"; break; }
  [[ -n "$failed" && "$failed" != "0" ]] && { echo "[k6] ${TESTID} 종료(threshold 실패 가능) — 결과는 회수한다"; break; }
  [[ $(date +%s) -gt $deadline ]] && { echo "[k6] ${TESTID} 타임아웃" >&2; break; }
  sleep 10
done

kubectl logs "job/k6-${TESTID}" -n "$NS" --tail=-1 > "${OUTDIR}/${TESTID}.log" 2>&1 || true

# 셸 트레이스(stderr)와 cat 출력(stdout)은 순서가 보장되지 않아 트레이스 줄이 JSON 중간에
# 끼어들 수 있다. 따라서 (1) '+ ' 트레이스 줄은 버리고, (2) 마커는 따옴표 없는 순수 줄만 인정하며,
# (3) 종료 마커가 JSON 마지막 문자에 붙어 나오는 경우(}===MARKER===)를 잘라낸다.
awk '
  /^\+ /                              { next }
  /^===K6_SUMMARY_JSON_BEGIN===$/     { f=1; next }
  f {
    if (index($0, "===K6_SUMMARY_JSON_END===")) {
      sub(/===K6_SUMMARY_JSON_END===.*/, "")
      if (length($0)) print
      exit
    }
    print
  }' "${OUTDIR}/${TESTID}.log" > "${OUTDIR}/${TESTID}.summary.json" || true

if [[ -s "${OUTDIR}/${TESTID}.summary.json" ]]; then
  echo "[k6] summary → ${OUTDIR}/${TESTID}.summary.json"
  # 규칙: dropped_iterations 가 0이 아니면 제공 부하가 목표에 못 미친 것이다.
  jq -r '
    "  p95=\(.metrics.http_req_duration.["p(95)"] // "n/a")  p99=\(.metrics.http_req_duration.["p(99)"] // "n/a")",
    "  waiting p95=\(.metrics.http_req_waiting.["p(95)"] // "n/a")  blocked p95=\(.metrics.http_req_blocked.["p(95)"] // "n/a")",
    "  dropped_iterations=\(.metrics.dropped_iterations.count // 0)"
  ' "${OUTDIR}/${TESTID}.summary.json" 2>/dev/null || true
else
  echo "[k6] ⚠ summary 회수 실패 — ${OUTDIR}/${TESTID}.log 확인" >&2
fi
