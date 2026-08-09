#!/usr/bin/env bash
# 클러스터가 이미 떠 있고 시드도 끝난 상태에서 시나리오만 다시 돌린다.
# (run-r2.sh 는 클러스터 생성부터 하므로 재실행에 쓸 수 없다)
#
# 전제: 인프라/서비스 Running, product_variant 시드 완료, k6-scripts ConfigMap 존재.

set -uo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

NS=ecommerce
PROJECT="$(gcloud config get-value project 2>/dev/null)"
REG="asia-northeast3-docker.pkg.dev/${PROJECT}/ecommerce"
TAG=loadtest-r1
PESS_TAG=loadtest-r1-pess
OUT="docs/loadtest/runs/$(date +%Y-%m-%d)-r2"
mkdir -p "$OUT"/{k6,metrics,db}

log() { echo -e "\n=== [$(date -u +%H:%M:%S)] $* ==="; }

reset_state() {
  # 재고는 총 제공 요청 수(hot-row-rampup ≈ 44k)를 넉넉히 넘겨야 한다.
  # 재고가 모자라면 락 경합이 아니라 '재고 소진 후 거절 경로'를 측정하게 된다(R2 1차 실패 원인).
  kubectl exec -n "$NS" mysql-product-0 -- mysql -usa -pchangeme -e \
    "USE ecommerce_product; UPDATE product_variant SET stock_quantity=500000 WHERE id=1; DELETE FROM stock_reservation;" 2>&1 | grep -v Warning || true
  kubectl exec -n "$NS" redis-product-0 -- redis-cli FLUSHALL >/dev/null 2>&1 || true
  sleep 3
}

swap_product() { # swap_product <tag>
  kubectl set image deploy/service-product "service-product=${REG}/service-product:$1" -n "$NS" >/dev/null
  kubectl rollout status deploy/service-product -n "$NS" --timeout=600s
}

log "S1-a  Atomic UPDATE"
swap_product "$TAG"
reset_state
./scripts/loadtest/run-scenario.sh s1-atomic hot-row-rampup.js "$OUT/k6" VARIANT_ID=1
./scripts/loadtest/collect-metrics.sh s1-atomic "$OUT/metrics" 10m

log "S1-b  비관적 락"
swap_product "$PESS_TAG"
reset_state
./scripts/loadtest/run-scenario.sh s1-pessimistic hot-row-rampup.js "$OUT/k6" VARIANT_ID=1
./scripts/loadtest/collect-metrics.sh s1-pessimistic "$OUT/metrics" 10m

log "S2-a  동기 DB 예약"
swap_product "$TAG"
kubectl set env deploy/service-product RESERVE_SETTLE_MODE=sync -n "$NS" >/dev/null
kubectl rollout status deploy/service-product -n "$NS" --timeout=600s
reset_state
./scripts/loadtest/run-scenario.sh s2-sync flash-sale-spike.js "$OUT/k6" VARIANT_ID=1
./scripts/loadtest/collect-metrics.sh s2-sync "$OUT/metrics" 5m

log "S2-b  Redis-only 예약"
kubectl set env deploy/service-product RESERVE_SETTLE_MODE=async -n "$NS" >/dev/null
kubectl rollout status deploy/service-product -n "$NS" --timeout=600s
reset_state
./scripts/loadtest/run-scenario.sh s2-async flash-sale-spike.js "$OUT/k6" VARIANT_ID=1
./scripts/loadtest/collect-metrics.sh s2-async "$OUT/metrics" 5m

log "정합성 검증"
kubectl exec -n "$NS" mysql-product-0 -- mysql -usa -pchangeme -N -e \
  "SELECT MIN(stock_quantity) FROM ecommerce_product.product_variant;" 2>&1 | grep -v Warning \
  > "$OUT/db/oversell.txt" || true
cat "$OUT/db/oversell.txt"

./scripts/loadtest/capture-env.sh r2 post "$NS" || true
log "완료 — $OUT"
