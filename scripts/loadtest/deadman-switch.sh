#!/usr/bin/env bash
# Dead-man's switch — 부하 테스트 세션이 어떤 이유로든 끊겨도 클러스터가 살아남지 않게 한다.
#
# 존재 이유: GKE 런은 생성→빌드→배포→측정→teardown의 긴 사슬이다. 중간에 세션이 죽거나
# 에러로 멈추면 클러스터는 그대로 과금된다. 이 스크립트는 세션과 무관하게 살아남아
# 지정 시간 뒤 클러스터를 무조건 삭제한다.
#
# 사용법:
#   arm:     scripts/loadtest/deadman-switch.sh arm <cluster> <zone> <minutes>
#   disarm:  scripts/loadtest/deadman-switch.sh disarm
#   status:  scripts/loadtest/deadman-switch.sh status

set -uo pipefail

STATE_DIR="${TMPDIR:-/tmp}/loadtest-deadman"
PID_FILE="$STATE_DIR/pid"
LOG_FILE="$STATE_DIR/log"
INFO_FILE="$STATE_DIR/info"

mkdir -p "$STATE_DIR"

cmd="${1:-status}"

case "$cmd" in
  arm)
    CLUSTER="${2:?cluster name required}"
    ZONE="${3:?zone required}"
    MINUTES="${4:-120}"

    if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
      echo "이미 armed 상태다 (pid $(cat "$PID_FILE")). 먼저 disarm 하라." >&2
      exit 1
    fi

    rm -f "$PID_FILE"
    # 셸/세션에서 분리해 부모가 죽어도 살아남게 한다.
    # setsid는 리눅스에만 있고 macOS에는 없다. 없으면 nohup + background 로도
    # 세션 종료를 견딘다(검증 완료).
    # 어느 쪽이든 부모의 \$! 는 신뢰할 수 없으므로(setsid는 fork 후 종료)
    # 자식이 자기 PID를 직접 기록한다.
    if command -v setsid >/dev/null 2>&1; then
      DETACH=(setsid nohup)
    else
      DETACH=(nohup)
    fi

    "${DETACH[@]}" bash -c "
      echo \$\$ > '$PID_FILE'
      sleep \$(( $MINUTES * 60 ))
      echo \"[\$(date -u +%FT%TZ)] DEADMAN FIRED — force deleting cluster $CLUSTER\" >> '$LOG_FILE'
      gcloud container clusters delete '$CLUSTER' --zone '$ZONE' --quiet >> '$LOG_FILE' 2>&1
      echo \"[\$(date -u +%FT%TZ)] cluster delete exit=\$?\" >> '$LOG_FILE'
      # 동적 PersistentDisk는 pvc-<uuid> 이름이라 클러스터 이름 필터로는 안 잡힌다.
      for d in \$(gcloud compute disks list --filter='name~^pvc-' --format='value(name,zone)' 2>/dev/null | tr '\t' ',' ); do
        n=\${d%%,*}; z=\${d##*,}
        gcloud compute disks delete \"\$n\" --zone \"\$z\" --quiet >> '$LOG_FILE' 2>&1
      done
      echo \"[\$(date -u +%FT%TZ)] deadman cleanup done\" >> '$LOG_FILE'
      rm -f '$PID_FILE'
    " >/dev/null 2>&1 &

    # 자식이 PID를 기록할 때까지 잠깐 기다린다.
    for _ in 1 2 3 4 5 6 7 8 9 10; do
      [[ -s "$PID_FILE" ]] && break
      sleep 0.2
    done
    if [[ ! -s "$PID_FILE" ]]; then
      echo "ARM 실패 — 자식 프로세스가 PID를 기록하지 못했다" >&2
      exit 1
    fi
    {
      echo "cluster=$CLUSTER"
      echo "zone=$ZONE"
      echo "minutes=$MINUTES"
      echo "armed_at=$(date -u +%FT%TZ)"
      echo "fires_at=$(date -u -v+${MINUTES}M +%FT%TZ 2>/dev/null || date -u -d "+${MINUTES} minutes" +%FT%TZ)"
    } > "$INFO_FILE"

    echo "ARMED — pid $(cat "$PID_FILE")"
    cat "$INFO_FILE"
    ;;

  disarm)
    if [[ -f "$PID_FILE" ]]; then
      pid="$(cat "$PID_FILE")"
      if kill -0 "$pid" 2>/dev/null; then
        # 자식(sleep)까지 정리한다. setsid가 있으면 그룹 kill이 먹고,
        # 없으면 pkill -P 로 자식을 따로 거둔다.
        kill -TERM -- "-$pid" 2>/dev/null || true
        pkill -P "$pid" 2>/dev/null || true
        kill -TERM "$pid" 2>/dev/null || true
        echo "DISARMED (pid $pid)"
      else
        echo "이미 종료된 pid $pid — 상태 파일만 정리한다"
      fi
      rm -f "$PID_FILE"
    else
      echo "armed 상태가 아니다"
    fi
    ;;

  status)
    if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
      echo "ARMED (pid $(cat "$PID_FILE"))"
      cat "$INFO_FILE" 2>/dev/null
    else
      echo "not armed"
    fi
    if [[ -f "$LOG_FILE" ]]; then echo "--- log ---"; cat "$LOG_FILE"; fi
    ;;

  *)
    echo "usage: $0 {arm <cluster> <zone> <minutes>|disarm|status}" >&2
    exit 2
    ;;
esac
