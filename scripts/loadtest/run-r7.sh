#!/usr/bin/env bash
# R7 — S1(재고 핫키) + S2(선착순)를 다시 돌리되, **목적이 하나 더 있다: 실제 Grafana 근거 화면 확보.**
#
#   RUN_ID=r7 scripts/loadtest/run-r7.sh
#
# R6 까지는 summary JSON 만 회수해서 수치는 있었지만 대시보드 캡처가 없었다.
# 손으로 그린 차트는 면접관에게 '조작한 화면'으로 보이므로 근거가 못 된다.
# 그래서 이 런은 (1) k6 지표를 Prometheus 로 remote-write 하고,
# (2) 시나리오별 실행 구간을 windows.json 에 남기고,
# (3) **teardown 하지 않고 멈춰서** 사람이 Grafana 를 캡처할 수 있게 한다.
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

RUN_ID="${RUN_ID:-r7}"
CLUSTER="${CLUSTER:-loadtest-$RUN_ID}"
ZONE="${ZONE:-asia-northeast3-a}"
NS=ecommerce
PROJECT="$(gcloud config get-value project 2>/dev/null)"
REG="asia-northeast3-docker.pkg.dev/${PROJECT}/ecommerce"
TAG="${TAG:-loadtest-r1}"
PESS_TAG="${PESS_TAG:-loadtest-r1-pess}"
# 스위치 예산: 배포+시드+워밍업+시나리오4회+캡처+teardown 기준. 빌드는 포함하지 않는다.
DEADMAN_MIN="${DEADMAN_MIN:-150}"

OUT="docs/loadtest/runs/$(date +%Y-%m-%d)-${RUN_ID}"
mkdir -p "$OUT"/{k6,metrics,db}

# S1: R6 에서 두 arm 의 차이를 실제로 드러낸 프로파일. Atomic p95 44ms / 비관적 락 p95 10.9s.
HR_STAGES='[{"duration":"1m","target":10},{"duration":"2m","target":15},{"duration":"2m","target":20},{"duration":"2m","target":25},{"duration":"2m","target":30},{"duration":"2m","target":40}]'
# S2: R5 에서 확정된 프로파일 그대로.
FS_STAGES='[{"duration":"30s","target":3},{"duration":"10s","target":15},{"duration":"1m","target":15},{"duration":"20s","target":5},{"duration":"20s","target":0}]'

# 시나리오별 실행 구간(epoch ms). Grafana 시간 범위를 여기에 정확히 맞춰야 한다.
# 범위가 넓으면 rate()[1m] 평균이 유휴 구간에 희석돼 k6 summary 값과 어긋난다.
WINDOWS="$OUT/windows.json"
echo '{}' > "$WINDOWS"
mark() { # mark <name> <start_ms> <end_ms>
  jq --arg k "$1" --argjson s "$2" --argjson e "$3" '.[$k]={start:$s,end:$e}' "$WINDOWS" > "$WINDOWS.tmp" && mv "$WINDOWS.tmp" "$WINDOWS"
}
now_ms() { python3 -c 'import time;print(int(time.time()*1000))'; }
# 시나리오를 돌리고 구간을 기록한다. 앞뒤로 30s 여유를 둬 램프 시작/종료가 잘리지 않게 한다.
run_marked() { # run_marked <testid> <script> <env...>
  local id="$1"; shift; local script="$1"; shift
  local t0 t1; t0=$(now_ms)
  ./scripts/loadtest/run-scenario.sh "$id" "$script" "$OUT/k6" "$@"
  t1=$(now_ms)
  mark "$id" $((t0-30000)) $((t1+30000))
  echo "[window] $id  $(date -u -r $((t0/1000)) +%H:%M:%S) ~ $(date -u -r $((t1/1000)) +%H:%M:%S) UTC"
}
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
run_marked s1-atomic hot-row-rampup.js VARIANT_ID=1 STAGES="$HR_STAGES"
./scripts/loadtest/collect-metrics.sh s1-atomic "$OUT/metrics" 10m

log "S1-b  비관적 락 (이미지 교체)"
kubectl set image deploy/service-product "service-product=${REG}/service-product:${PESS_TAG}" -n "$NS"
kubectl rollout status deploy/service-product -n "$NS" --timeout=600s
reset_state
run_marked s1-pessimistic hot-row-rampup.js VARIANT_ID=1 STAGES="$HR_STAGES"
./scripts/loadtest/collect-metrics.sh s1-pessimistic "$OUT/metrics" 10m

# ── 9. S2 — 선착순: sync vs async (런타임 토글, 이미지 동일) ──────────────────
log "S2  Atomic 이미지로 복귀"
kubectl set image deploy/service-product "service-product=${REG}/service-product:${TAG}" -n "$NS"
kubectl rollout status deploy/service-product -n "$NS" --timeout=600s

log "S2-a  동기 DB 예약 (reserve.settle.mode=sync)"
kubectl set env deploy/service-product RESERVE_SETTLE_MODE=sync -n "$NS"
kubectl rollout status deploy/service-product -n "$NS" --timeout=600s
reset_state
run_marked s2-sync flash-sale-spike.js VARIANT_ID=1 SPIKE_STAGES="$FS_STAGES"
./scripts/loadtest/collect-metrics.sh s2-sync "$OUT/metrics" 5m

log "S2-b  Redis-only 예약 (reserve.settle.mode=async)"
kubectl set env deploy/service-product RESERVE_SETTLE_MODE=async -n "$NS"
kubectl rollout status deploy/service-product -n "$NS" --timeout=600s
reset_state
run_marked s2-async flash-sale-spike.js VARIANT_ID=1 SPIKE_STAGES="$FS_STAGES"
./scripts/loadtest/collect-metrics.sh s2-async "$OUT/metrics" 5m

# ── 10. 정합성 검증 ───────────────────────────────────────────────────────────
log "데이터 정합성 검증"
kubectl exec -n "$NS" mysql-product-0 -- mysql -usa -pchangeme -e \
  "USE ecommerce_product; SELECT MIN(stock_quantity) AS min_stock FROM product_variant;" \
  > "$OUT/db/oversell.txt" 2>/dev/null || true
cat "$OUT/db/oversell.txt" || true

log "환경 덤프 (post)"
./scripts/loadtest/capture-env.sh "$RUN_ID" post "$NS" || true

log "완료 — 산출물: $OUT"
echo
echo "실행 구간(windows.json):"
cat "$WINDOWS" | jq .
echo
echo "★ 클러스터를 일부러 살려 둔다. Grafana 근거 화면을 캡처한 뒤에 지운다."
echo "  1) kubectl -n monitoring port-forward svc/grafana 3000:3000 &"
echo "  2) 대시보드 http://localhost:3000/d/ecommerce-hotrow  (시간 범위는 windows.json 값)"
echo "  3) 캡처가 끝나면:"
echo "     gcloud container clusters delete $CLUSTER --zone $ZONE --quiet"
echo "     scripts/loadtest/deadman-switch.sh disarm"
echo "     gcloud compute disks list --filter='name~^pvc-'   # 0이어야 한다"
echo
echo "deadman 은 ${DEADMAN_MIN}분 뒤 자동 발화한다 — 캡처 중 세션이 죽어도 과금은 멈춘다."
