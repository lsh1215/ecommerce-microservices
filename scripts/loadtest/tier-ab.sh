#!/usr/bin/env bash
# NORMAL vs HOT 교차 반복 측정.
#
# 한 arm을 몰아서 돌리면 시간에 따라 변하는 요인(버퍼풀 상태, 테이블 조각화, 노드 이웃)이
# 전부 뒤에 도는 arm 쪽에 실린다. 교차로 돌리면 그 요인이 두 arm에 고르게 퍼진다.
#
# 사용법: tier-ab.sh <반복횟수> <NORMAL rate> <HOT rate>
set -u
cd "$(cd "$(dirname "$0")/../.." && pwd)"
REPS=${1:?반복 횟수}; NR=${2:?NORMAL rate}; HR=${3:?HOT rate}
for i in $(seq 1 "$REPS"); do
  bash scripts/loadtest/knee-ladder.sh 1 NORMAL "ab${i}n" "$NR"
  bash scripts/loadtest/knee-ladder.sh 2 HOT    "ab${i}h" "$HR"
done
