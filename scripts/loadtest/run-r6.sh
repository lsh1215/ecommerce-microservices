#!/usr/bin/env bash
# 재측정 R6 — S1(재고 핫키: 비관적 락 vs Atomic) 전용. knee 탐색용 고부하 램프.
#
#   RUN_ID=r6 scripts/loadtest/run-r6.sh
#
# R5에서 1→10 rps는 두 arm 모두 p95 50ms·실패 0으로 여유였고, R4의 45 rps는 전 구간 붕괴였다.
# 따라서 knee 는 10~45 rps 사이에 있다. 이 런은 그 구간을 훑는다. S2 는 R5 에서 확정됐으므로 돌리지 않는다.
#
# 전제: 이미지가 이미 Artifact Registry에 있다(R1에서 빌드 완료).
#   service-{order,product,payment,customer}:loadtest-r1
#   service-product:loadtest-r1-pess          ← S1 비교군(비관적 락)
# 코드가 안 바뀌었으면 다시 빌드하지 않는다 — 빌드는 측정과 분리한다(README §0.1).
#
# 실패 시 동작: 클러스터를 남긴다(디버깅용). 돈은 dead-man's switch가 막는다.
#   강제 정리: scripts/loadtest/deadman-switch.sh status 로 발화 시각 확인

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

RUN_ID="${RUN_ID:-r6}"
CLUSTER="${CLUSTER:-loadtest-$RUN_ID}"
ZONE="${ZONE:-asia-northeast3-a}"
NS=ecommerce
PROJECT="$(gcloud config get-value project 2>/dev/null)"
REG="asia-northeast3-docker.pkg.dev/${PROJECT}/ecommerce"
TAG="${TAG:-loadtest-r1}"
PESS_TAG="${PESS_TAG:-loadtest-r1-pess}"
# 스위치 예산: 배포+시드+워밍업+시나리오4회+캡처+teardown 기준. 빌드는 포함하지 않는다.
DEADMAN_MIN="${DEADMAN_MIN:-90}"

OUT="docs/loadtest/runs/$(date +%Y-%m-%d)-${RUN_ID}"
mkdir -p "$OUT"/{k6,metrics,db}

# R6: knee 는 10~45 rps 사이. 2분 간격으로 훑어 p95 상승 변곡을 찾는다.
HR_STAGES='[{"duration":"1m","target":10},{"duration":"2m","target":15},{"duration":"2m","target":20},{"duration":"2m","target":25},{"duration":"2m","target":30},{"duration":"2m","target":40}]'
log() { echo -e "\n=== [$(date -u +%H:%M:%S)] $* ==="; }

# ── 0. preflight ──────────────────────────────────────────────────────────────
log "preflight"
for t in gcloud kubectl jq k6; do command -v "$t" >/dev/null || { echo "missing: $t" >&2; exit 1; }; done
gcloud container clusters list >/dev/null 2>&1 || { echo "gcloud 인증 실패" >&2; exit 1; }
for i in service-order:$TAG service-product:$TAG service-payment:$TAG service-customer:$TAG service-product:$PESS_TAG; do
  gcloud artifacts docker images describe "${REG}/${i}" >/dev/null 2>&1 \
    || { echo "이미지 없음: ${REG}/${i} — 먼저 빌드하라" >&2; exit 1; }
done
echo "이미지 5종 확인 완료"

# ── 1. dead-man's switch ──────────────────────────────────────────────────────
log "dead-man's switch arm (${DEADMAN_MIN}분)"
./scripts/loadtest/deadman-switch.sh arm "$CLUSTER" "$ZONE" "$DEADMAN_MIN"

# ── 2. 클러스터 ───────────────────────────────────────────────────────────────
log "클러스터 생성 ($CLUSTER)"
gcloud container clusters create "$CLUSTER" --zone "$ZONE" \
  --num-nodes 4 --machine-type e2-standard-8 \
  --disk-type pd-balanced --disk-size 50 \
  --no-enable-autoupgrade --no-enable-autorepair --quiet

# ── 2b. 노드 역할 지정 ────────────────────────────────────────────────────────
# MySQL은 role=db, Redis는 role=redis 라벨 노드를 요구한다(매니페스트 nodeSelector).
# 라벨이 없으면 Pending으로 멈춘다 — R2 1차에서 17분을 여기서 잃었다.
# k6는 전용 노드에 taint로 격리한다: 부하 생성기가 서비스와 CPU를 다투면
# 제공 부하가 목표에 미달해(dropped_iterations) 측정 자체가 무의미해진다.
log "노드 역할 라벨/taint"
# mapfile은 bash 4+ 전용이라 macOS 기본 bash 3.2에서 깨진다. 이식성 있게 배열을 만든다.
NODE_LIST=()
while IFS= read -r n; do [ -n "$n" ] && NODE_LIST+=("$n"); done < <(kubectl get nodes --no-headers -o custom-columns=":.metadata.name")
[ "${#NODE_LIST[@]}" -ge 4 ] || { echo "노드가 4개 미만이다: ${#NODE_LIST[@]}" >&2; exit 1; }
kubectl label node "${NODE_LIST[0]}" role=db --overwrite
kubectl label node "${NODE_LIST[1]}" role=redis --overwrite
kubectl label node "${NODE_LIST[2]}" role=loadgen --overwrite
kubectl taint node "${NODE_LIST[2]}" dedicated=loadgen:NoSchedule --overwrite
echo "노드 역할: db=${NODE_LIST[0]} redis=${NODE_LIST[1]} loadgen=${NODE_LIST[2]}(taint) services=${NODE_LIST[3]}"

# ── 3. 인프라 + 모니터링 ──────────────────────────────────────────────────────
log "인프라/모니터링 배포"
kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -k k8s/base -n "$NS"
kubectl apply -f k8s/monitoring/namespace.yml
for f in prometheus grafana loki alloy otel-config-active; do kubectl apply -f "k8s/monitoring/${f}.yml"; done
kubectl apply -f k8s/monitoring/dashboards/

log "인프라 Ready 대기"
kubectl wait --for=condition=ready pod -l app=mysql-product -n "$NS" --timeout=600s || true
until [ "$(kubectl get pods -n "$NS" --no-headers | grep -cv 'Running\|Completed')" = "0" ]; do sleep 10; done
echo "인프라 준비 완료"

# ── 4. 서비스 배포 ────────────────────────────────────────────────────────────
log "서비스 배포 (부하테스트 프로파일: order/product 2000m)"
kubectl apply -k k8s/overlays/loadtest
kubectl rollout status deploy/service-product -n "$NS" --timeout=600s
kubectl rollout status deploy/service-order -n "$NS" --timeout=600s

log "환경 덤프 (pre)"
./scripts/loadtest/capture-env.sh "$RUN_ID" pre "$NS" || true

# ── 5. 시드 ───────────────────────────────────────────────────────────────────
log "시드 데이터 적재"
seed_db() { # seed_db <db> <pod>
  awk -v db="$1" 'BEGIN{f=0} /^USE /{f=($0 ~ "USE "db";")} f' scripts/seed-data.sql \
    | kubectl exec -i -n "$NS" "$2" -- mysql -usa -pchangeme 2>/dev/null || true
}
seed_db ecommerce_product  mysql-product-0
seed_db ecommerce_customer mysql-customer-0

# R2 1차 실패 원인: 자격증명 오류로 시드가 실패했는데 `2>/dev/null || true`가 이를 삼켜
# 상품 없는 상태로 측정이 진행됐다(34,977건 전부 4xx). 치명적 단계는 반드시 검증한다.
SEED_COUNT="$(kubectl exec -n "$NS" mysql-product-0 -- mysql -usa -pchangeme -N -e \
  "SELECT COUNT(*) FROM ecommerce_product.product_variant;" 2>/dev/null | tr -d '[:space:]')"
if [ -z "$SEED_COUNT" ] || [ "$SEED_COUNT" -lt 1 ] 2>/dev/null; then
  echo "시드 검증 실패: product_variant 행이 없다 (count='$SEED_COUNT'). 중단한다." >&2
  exit 1
fi
echo "시드 검증 통과: product_variant ${SEED_COUNT}행"

reset_state() {
  # 런 사이 초기 상태를 동일하게 되돌린다(G005와 동일 절차). 이걸 빼면 런끼리 비교가 불가능하다.
  kubectl exec -n "$NS" mysql-product-0 -- mysql -usa -pchangeme -e \
    "USE ecommerce_product; UPDATE product_variant SET stock_quantity=500000 WHERE id=1; DELETE FROM stock_reservation;" 2>/dev/null || true
  kubectl exec -n "$NS" redis-product-0 -- redis-cli FLUSHALL >/dev/null 2>&1 || true
  sleep 3
}

# ── 6. k6 스크립트 ConfigMap ──────────────────────────────────────────────────
log "k6 스크립트 ConfigMap"
kubectl delete configmap k6-scripts -n "$NS" --ignore-not-found
kubectl create configmap k6-scripts --from-file=k6/scripts -n "$NS"

# ── 7. 워밍업 (측정 아님 — 결과는 버린다) ─────────────────────────────────────
log "JVM 워밍업"
./scripts/loadtest/run-scenario.sh warmup cascading-failure.js "$OUT/k6" RATE=10 DURATION=60s || true
reset_state

# ── 8. S1 — 재고 핫키: 비관적 락 vs Atomic ────────────────────────────────────
log "S1-a  Atomic UPDATE"
reset_state
./scripts/loadtest/run-scenario.sh s1-atomic hot-row-rampup.js "$OUT/k6" VARIANT_ID=1 STAGES="$HR_STAGES"
./scripts/loadtest/collect-metrics.sh s1-atomic "$OUT/metrics" 12m

log "S1-b  비관적 락 (이미지 교체)"
kubectl set image deploy/service-product "service-product=${REG}/service-product:${PESS_TAG}" -n "$NS"
kubectl rollout status deploy/service-product -n "$NS" --timeout=600s
reset_state
./scripts/loadtest/run-scenario.sh s1-pessimistic hot-row-rampup.js "$OUT/k6" VARIANT_ID=1 STAGES="$HR_STAGES"
./scripts/loadtest/collect-metrics.sh s1-pessimistic "$OUT/metrics" 12m

# ── 10. 정합성 검증 ───────────────────────────────────────────────────────────
log "데이터 정합성 검증"
kubectl exec -n "$NS" mysql-product-0 -- mysql -usa -pchangeme -e \
  "USE ecommerce_product; SELECT MIN(stock_quantity) AS min_stock FROM product_variant;" \
  > "$OUT/db/oversell.txt" 2>/dev/null || true
cat "$OUT/db/oversell.txt" || true

log "환경 덤프 (post)"
./scripts/loadtest/capture-env.sh "$RUN_ID" post "$NS" || true

# ── 11. 자동 teardown ─────────────────────────────────────────────────────────
# R6 는 백그라운드로 돌기 때문에 사람이 teardown 을 놓칠 수 있다. 측정이 끝났으면 바로 지운다.
# 실패로 중단된 경우엔 이 지점에 도달하지 않으므로 클러스터가 남고(디버깅용) deadman 이 처리한다.
log "teardown"
gcloud container clusters delete "$CLUSTER" --zone "$ZONE" --quiet || true
./scripts/loadtest/deadman-switch.sh disarm || true
echo "-- 잔여 리소스 확인 --"
gcloud container clusters list || true
gcloud compute disks list --filter="name~^pvc-" || true

log "완료 — 산출물: $OUT"
