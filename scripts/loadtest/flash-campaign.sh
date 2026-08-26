#!/usr/bin/env bash
# 선착순 공정성 캠페인.
#
# 묻는 것은 처리량이 아니라 공정성과 정합성이다. 재고 100~1,000 개에 90만 요청이 오므로
# "몇 rps 를 냈나"가 아니라 "누가 이겼나"가 결과다.
#
# 런마다 이 순서를 지킨다.
#   1) 리셋: 승자 표 비우고, 유닛 다시 깔고, 토픽 다시 만들고, 매진 플래그 해제
#   2) 워밍업: 목표의 10%에서 100%까지 램프. 식은 JVM 에 곧장 목표를 때리면 워밍업 자체가
#      포화 상태로 돌아 회복하지 못한다
#   3) 리셋 한 번 더: 워밍업이 재고를 다 먹었으므로
#   4) 측정
#   5) 검증: 승자 offset 이 파티션의 첫 N 개와 일치하는지 DB 에서 직접 확인
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"
HERE="$ROOT/scripts/loadtest"
OUTROOT="${OUTROOT:-docs/evidence/flash-kafka/$(date +%Y%m%d-%H%M)}"
SCEN="k6/scripts/flash-spike.js"
PREFLIGHT="$HOME/.claude/skills/loadtest-preflight/scripts/preflight.py"
RATE="${RATE:-3000}"
DURATION="${DURATION:-300s}"
mkdir -p "$OUTROOT"

run_one() { # label variantId stock
  local label="$1" variant="$2" stock="$3"
  local out="$OUTROOT/$label"
  mkdir -p "$out"
  echo "===== $label  재고 $stock =====" 

  bash "$HERE/flash-reset.sh" "$variant" "$stock"

  echo "[warmup] ramp -> $RATE"
  WARMUP=1 bash "$HERE/run-k6-job.sh" "$SCEN" "$OUTROOT/_warmup" \
    ORDER_API=http://service-order:8082 VARIANT_ID="$variant" RATE="$RATE" WARM=1 \
    >/dev/null 2>&1 &
  local warm_pid=$!

  # 생성기 격리는 생성기가 실제로 돌 때만 확인할 수 있다. 그래서 환경 게이트를 워밍업
  # 중에 돌린다. 워밍업 토큰도 여기서 남긴다 - 측정 직전에 그 토큰으로 "워밍업 이후
  # 파드가 재시작되지 않았는지"를 본다.
  for _ in $(seq 1 60); do
    [ -n "$(kubectl -n ecommerce get pods -l app=k6 --field-selector=status.phase=Running \
            -o name 2>/dev/null)" ] && break
    sleep 2
  done
  python3 "$PREFLIGHT" --namespace ecommerce --profile evidence \
    --db-cores 2 --loadgen-selector app=k6 \
    --record-warmup "$out/warmup-token.json" \
    --fingerprint-out "$out/fingerprint.json" 2>&1 | tee "$out/preflight.txt" | tail -20
  local pf=${PIPESTATUS[0]}
  wait "$warm_pid" || true
  if [ "$pf" -ne 0 ]; then
    echo "[$label] 환경 게이트 미통과. 측정하지 않는다." >&2
    return 1
  fi

  # 워밍업이 재고를 다 먹었다. 측정은 재고가 가득한 상태에서 시작해야 한다.
  bash "$HERE/flash-reset.sh" "$variant" "$stock"

  # 워밍업 이후 파드가 재시작됐으면 콜드로 돌아간 채 재는 것이다. 여기서 막는다.
  python3 "$PREFLIGHT" --namespace ecommerce --profile standard \
    --db-cores 2 --verify-warmup "$out/warmup-token.json" 2>&1 | tail -6

  local rc=0
  MAX_VUS=$(( RATE * 5 < 6000 ? RATE * 5 : 6000 )) REQUIRE_MYSQL=true \
    bash "$HERE/run-k6-job.sh" "$SCEN" "$out" \
      ORDER_API=http://service-order:8082 VARIANT_ID="$variant" \
      RATE="$RATE" DURATION="$DURATION" ARM="$label" STOCK="$stock" || rc=$?
  echo "$rc" > "$out/RUNNER_RC"
  [ "$rc" -eq 0 ] || echo "[$label] 러너 종료코드 $rc — 수집은 계속한다."

  python3 "$HERE/run-metrics.py" "$out" "$label" || true

  echo "[$label] 공정성·정합성 검증"
  python3 "$HERE/flash-verify.py" "$variant" "$stock" "$out/verify.json" || true
}

case "${ONLY:-all}" in
  stock1000) run_one "stock1000" 2 1000 ;;
  stock100)  run_one "stock100"  1 100 ;;
  *)         run_one "stock1000" 2 1000; run_one "stock100"  1 100 ;;
esac

echo "===== CAMPAIGN DONE -> $OUTROOT ====="
