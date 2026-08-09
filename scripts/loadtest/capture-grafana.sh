#!/usr/bin/env bash
# 실행이 끝난 클러스터의 Grafana 에서 포트폴리오 근거 화면을 PNG 로 받아온다.
#
#   scripts/loadtest/capture-grafana.sh <run-dir> <out-dir>
#   예: scripts/loadtest/capture-grafana.sh docs/loadtest/runs/2026-08-01-r7 docs/loadtest/runs/2026-08-01-r7/shots
#
# 왜 이렇게 하나:
# - summary JSON 은 수치를 주지만 화면을 주지 않는다. 손으로 그린 차트는 출처가 없어 근거가 못 된다.
# - 로컬 브라우저 + port-forward 로 Grafana SPA 를 띄우면 에셋 요청이 몰려 터널이 막히고 빈 화면이 찍힌다.
#   그래서 **클러스터 안의 grafana-image-renderer 에 렌더링을 시키고 PNG 만 받는다**(요청 1회).
# - Grafana 가 sub_path(/grafana)로 서비스되므로 렌더 경로에도 접두사가 필요하다.
#
# 시간 범위는 run-r7.sh 가 남긴 windows.json 을 그대로 쓴다. 범위를 넓게 잡으면
# rate()[1m] 이 유휴 구간에 희석돼 k6 summary 와 어긋난다.

set -euo pipefail

RUN_DIR="${1:?run dir required (windows.json 이 있는 곳)}"
OUT_DIR="${2:?output dir required}"
WINDOWS="$RUN_DIR/windows.json"
[ -s "$WINDOWS" ] || { echo "windows.json 이 없다: $WINDOWS" >&2; exit 1; }

PORT="${PORT:-3000}"
PREFIX="${PREFIX:-/grafana}"          # GF_SERVER_SERVE_FROM_SUB_PATH=true
BASE="http://localhost:${PORT}${PREFIX}"
UID_DASH="${UID_DASH:-ecommerce-hotrow}"
mkdir -p "$OUT_DIR"

# ── port-forward ──────────────────────────────────────────────────────────────
if ! curl -sf --max-time 4 "${BASE}/api/health" >/dev/null 2>&1; then
  echo "[pf] grafana port-forward :${PORT}"
  kubectl -n monitoring port-forward svc/grafana "${PORT}:3000" >/tmp/grafana-pf.log 2>&1 &
  PF_PID=$!
  trap 'kill $PF_PID 2>/dev/null || true' EXIT
  for _ in $(seq 1 40); do curl -sf --max-time 3 "${BASE}/api/health" >/dev/null 2>&1 && break; sleep 1; done
fi
curl -sf --max-time 5 "${BASE}/api/health" >/dev/null || { echo "grafana 에 붙지 못했다" >&2; exit 1; }
echo "[pf] grafana ok"

win() { jq -r --arg k "$1" ".[\$k].$2 // \"null\"" "$WINDOWS"; }

# render <출력파일> <경로> <쿼리> [width] [height]
render() {
  local out="$1" path="$2" q="$3" w="${4:-1500}" h="${5:-620}"
  local url="${BASE}/render/${path}?${q}&width=${w}&height=${h}&tz=UTC"
  curl -s --max-time 180 "$url" -o "$out"
  if file "$out" | grep -q "PNG image"; then
    echo "  ✓ $(basename "$out")  ($(du -h "$out" | cut -f1))"
  else
    echo "  ✗ $(basename "$out") — PNG 이 아니다:"; head -c 300 "$out"; echo; return 1
  fi
}

span_start() { local a b; a=$(win "$1" start); b=$(win "$2" start); [ "$a" -lt "$b" ] && echo "$a" || echo "$b"; }
span_end()   { local a b; a=$(win "$1" end);   b=$(win "$2" end);   [ "$a" -gt "$b" ] && echo "$a" || echo "$b"; }

# 패널 id 는 dashboard-ecommerce-hotrow.yml 정의와 일치해야 한다.
#   1 처리량 · 2 응답시간 p95/p99 · 3 제공부하 · 4 실패율
#   5 InnoDB 행 락 대기시간 · 6 행 락 대기 발생 · 7 HikariCP · 8 CPU 스로틀 · 9 product DB 쓰기량
capture_pair() { # capture_pair <prefix> <armA> <armB>
  local pfx="$1" a="$2" b="$3"
  [ "$(win "$a" start)" = "null" ] && { echo "[$pfx] windows 없음 — 건너뜀"; return 0; }
  [ "$(win "$b" start)" = "null" ] && { echo "[$pfx] windows 없음 — 건너뜀"; return 0; }
  local f t q
  f=$(span_start "$a" "$b"); t=$(span_end "$a" "$b")
  q="from=${f}&to=${t}&var-testid=${a}&var-testid=${b}"
  echo "[$pfx] ${f} ~ ${t}  (${a} vs ${b})"
  render "$OUT_DIR/${pfx}-dashboard.png"  "d/${UID_DASH}/x"      "${q}&kiosk" 1600 1900 || true
  render "$OUT_DIR/${pfx}-latency.png"    "d-solo/${UID_DASH}/x" "${q}&panelId=2" || true
  render "$OUT_DIR/${pfx}-throughput.png" "d-solo/${UID_DASH}/x" "${q}&panelId=1" || true
  render "$OUT_DIR/${pfx}-failrate.png"   "d-solo/${UID_DASH}/x" "${q}&panelId=4" || true
  render "$OUT_DIR/${pfx}-offered.png"    "d-solo/${UID_DASH}/x" "${q}&panelId=3" || true
  render "$OUT_DIR/${pfx}-rowlock.png"    "d-solo/${UID_DASH}/x" "${q}&panelId=5" || true
  render "$OUT_DIR/${pfx}-rowlock-waits.png" "d-solo/${UID_DASH}/x" "${q}&panelId=6" || true
  render "$OUT_DIR/${pfx}-hikari.png"     "d-solo/${UID_DASH}/x" "${q}&panelId=7" || true
  render "$OUT_DIR/${pfx}-throttle.png"   "d-solo/${UID_DASH}/x" "${q}&panelId=8" || true
  render "$OUT_DIR/${pfx}-dbwrite.png"    "d-solo/${UID_DASH}/x" "${q}&panelId=9" || true
  render "$OUT_DIR/${pfx}-queueing.png"   "d-solo/${UID_DASH}/x" "${q}&panelId=11" || true
  render "$OUT_DIR/${pfx}-acquire.png"    "d-solo/${UID_DASH}/x" "${q}&panelId=12" || true
}

capture_pair s1 s1-atomic s1-pessimistic
capture_pair s2 s2-sync   s2-async

echo
echo "완료 — $OUT_DIR"
ls -la "$OUT_DIR"
