#!/usr/bin/env bash
# Knee ladder for one arm.
#   ladder.sh <variant> <tier> <armprefix> <rate>...
# Reseeds before every step so stock depletion never masquerades as a
# throughput limit, waits for the SUT to drain between steps, and records
# server-side metrics per step.
set -u
cd "$(cd "$(dirname "$0")/../.." && pwd)"
VARIANT=$1; TIER=$2; PREFIX=$3; shift 3
D=docs/evidence/latest/rev8/knee
PP=$(kubectl get pod -n monitoring -l app=prometheus -o jsonpath='{.items[0].metadata.name}')

# A step past the knee leaves queued work behind: Hikari waiters, InnoDB lock
# waiters, and in-flight requests the client already abandoned. Starting the
# next step into that backlog produced a run where every single request timed
# out at a rate the same arm served cleanly one step later. Wait for the
# system to be genuinely idle instead of assuming it is.
settle() {
  # 기대 파드 수는 배포에서 읽는다. 예전에는 2로 박혀 있어서 레플리카가 2가 아닌
  # 구성에서는 조건이 영원히 참이 되지 않았고, 스텝마다 6분을 헛기다린 뒤 경고만
  # 남기고 넘어갔다. 기다림이 무의미해지면 다음 스텝이 이전 스텝의 backlog 위로
  # 들어간다.
  want=$(kubectl get deploy service-product -n ecommerce -o jsonpath='{.spec.replicas}')
  for _ in $(seq 1 24); do
    ready=$(kubectl get pods -n ecommerce -l app=service-product --no-headers |
      grep -c "1/1 *Running")
    pending=$(kubectl exec -n monitoring "$PP" -c prometheus -- wget -qO- \
      'http://localhost:9090/api/v1/query?query=sum(hikaricp_connections_pending)' 2>/dev/null |
      sed -n 's/.*"value":\[[^,]*,"\([0-9.]*\)".*/\1/p')
    running=$(kubectl exec -n ecommerce mysql-product-0 -- sh -c \
      'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME=\"Threads_running\""' 2>/dev/null | tr -d '\r')
    # 시드가 남긴 더티 페이지가 빠질 때까지 기다린다. 이것을 안 보면 측정이 시드의
    # 플러시와 겹치고, 그 겹침의 정도가 런마다 달라 지연이 흔들린다.
    dirty=$(kubectl exec -n ecommerce mysql-product-0 -- sh -c \
      'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME=\"Innodb_buffer_pool_pages_dirty\""' 2>/dev/null | tr -d '\r')
    if [ "$ready" = "$want" ] && [ "${pending:-9}" = "0" ] && [ "${running:-99}" -le 3 ] \
       && [ "${dirty:-99999}" -le 100 ]; then
      sleep 30
      return
    fi
    sleep 15
  done
  echo "  (경고: 회복 대기 시간 초과 — ready=$ready pending=${pending:-?} threads_running=${running:-?} dirty=${dirty:-?})"
}

# preAllocatedVUs는 리틀의 법칙으로 잡는다: 필요한 동시성 = 도착률 x 응답시간.
# 예전 값 R*5는 요청당 200ms를 가정한 것인데 이 경로는 20ms 안팎이므로 100배 과다였다.
# 400 rps에서 VU 2,000개를 초기화하는 비용이 초반 수백 ms의 지연을 만들었고, 그것이
# 서버 지연으로 기록됐다. 부하가 높은 arm일수록 크게 걸리므로 비교를 한쪽으로 기울인다.
# maxVUs는 넉넉히 남겨 두어 시스템이 느려지면 k6가 VU를 늘릴 수 있게 한다.
LATENCY_S=${LATENCY_S:-0.05}
for R in "$@"; do
  PRE_VUS=$(python3 -c "import math,sys; print(max(20, math.ceil(float(sys.argv[1])*float(sys.argv[2])*3)))" "$R" "$LATENCY_S")
  # 시드를 먼저, 진정을 나중에. 예전 순서(진정 -> 시드 -> 측정)는 조용해질 때까지 기다린
  # 다음 stock_unit 100만 행을 지우고 다시 넣고 곧바로 쟀다. 측정이 그 쓰기의 플러시
  # 위에서 시작되므로, 기다린 의미가 없어진다.
  bash scripts/loadtest/seed-tiers.sh >/dev/null 2>&1
  settle
  OUT=$D/${PREFIX}-${R}
  rm -rf "$OUT"
  mkdir -p "$OUT/logs"
  # Keep the runner log with the run it belongs to. A scratch path in /tmp
  # separates the failure reason from the evidence that explains it.
  LOG=$OUT/logs/run.log
  bash scripts/loadtest/run-k6-job.sh k6/scripts/reserve-tier.js "$OUT" \
    VARIANT="$VARIANT" RATE="$R" DURATION=120s PRE_VUS="$PRE_VUS" MAX_VUS=$((R * 15)) \
    REPLICA=off ARM="${PREFIX}-${R}" TIER="$TIER" >"$LOG" 2>&1
  if [ $? -ne 0 ]; then
    echo "  ${PREFIX}@${R}: 무릎 초과 — $(grep -oE 'http_req_failed=[0-9.]+%' "$LOG" | head -1) 실패"
    continue
  fi
  python3 scripts/loadtest/run-metrics.py "$OUT" "${PREFIX}@${R}"
done
