#!/usr/bin/env bash
# R7 추가 시행 — 비관적 락 arm 을 한 번 더 돌린다. **재현성 판정용.**
#
#   scripts/loadtest/run-r7-repeat-pess.sh
#
# 왜: 같은 코드·같은 부하 프로파일인데 두 런의 결과가 250배 차이났다.
#   R6 비관적 락  p95 10,943ms · dropped 481 · timeout 766
#   R7 비관적 락  p95     40.5ms · dropped   0 · timeout   0
# 둘 중 하나는 환경 요인이다. 어느 쪽이 이상치인지 세 번째 표본으로 가른다.
# 판정을 세우기 전까지 슬라이드 8 에는 아무 수치도 쓰지 않는다.
#
# 전제: loadtest-r7 클러스터가 살아 있다. S2 직후라 배포 이미지는 Atomic 이므로 교체가 필요하다.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

NS=ecommerce
PROJECT="$(gcloud config get-value project 2>/dev/null)"
REG="asia-northeast3-docker.pkg.dev/${PROJECT}/ecommerce"
PESS_TAG="${PESS_TAG:-loadtest-r1-pess}"
OUT="docs/loadtest/runs/2026-08-01-r7"
WINDOWS="$OUT/windows.json"
TESTID="${TESTID:-s1-pessimistic-2}"

# R7 본 런과 동일한 프로파일이어야 비교가 성립한다.
HR_STAGES='[{"duration":"1m","target":10},{"duration":"2m","target":15},{"duration":"2m","target":20},{"duration":"2m","target":25},{"duration":"2m","target":30},{"duration":"2m","target":40}]'

log() { echo -e "\n=== [$(date -u +%H:%M:%S)] $* ==="; }
now_ms() { python3 -c 'import time;print(int(time.time()*1000))'; }
mark() {
  jq --arg k "$1" --argjson s "$2" --argjson e "$3" '.[$k]={start:$s,end:$e}' "$WINDOWS" > "$WINDOWS.tmp" && mv "$WINDOWS.tmp" "$WINDOWS"
}

kubectl config current-context | grep -q loadtest-r7 || { echo "loadtest-r7 컨텍스트가 아니다" >&2; exit 1; }

log "비관적 락 이미지로 교체"
kubectl set image deploy/service-product "service-product=${REG}/service-product:${PESS_TAG}" -n "$NS"
kubectl rollout status deploy/service-product -n "$NS" --timeout=600s

log "상태 리셋"
kubectl exec -n "$NS" mysql-product-0 -- mysql -usa -pchangeme -e \
  "USE ecommerce_product; UPDATE product_variant SET stock_quantity=500000 WHERE id=1; DELETE FROM stock_reservation;" 2>/dev/null || true
kubectl exec -n "$NS" redis-product-0 -- redis-cli FLUSHALL >/dev/null 2>&1 || true
sleep 5

log "$TESTID 실행"
T0=$(now_ms)
./scripts/loadtest/run-scenario.sh "$TESTID" hot-row-rampup.js "$OUT/k6" VARIANT_ID=1 STAGES="$HR_STAGES"
T1=$(now_ms)
mark "$TESTID" $((T0-30000)) $((T1+30000))
./scripts/loadtest/collect-metrics.sh "$TESTID" "$OUT/metrics" 12m

log "세 시행 비교"
for f in docs/loadtest/runs/2026-08-01-r6/k6/s1-pessimistic.summary.json \
         docs/loadtest/runs/2026-08-01-r7/k6/s1-pessimistic.summary.json \
         "$OUT/k6/${TESTID}.summary.json"; do
  [ -s "$f" ] || continue
  echo -n "$(dirname $(dirname $f) | xargs basename)/$(basename $f .summary.json): "
  jq -r '"p50=\(.metrics.http_req_duration.med|.*10|round/10) p95=\(.metrics.http_req_duration["p(95)"]|.*10|round/10) p99=\(.metrics.http_req_duration["p(99)"]|.*10|round/10) ok=\(.metrics.orders_created_2xx.count) dropped=\(.metrics.dropped_iterations.count//0) timeout=\(.metrics.orders_timeout.count//0)"' "$f"
done

log "완료 — teardown 은 캡처 후 수동으로"
