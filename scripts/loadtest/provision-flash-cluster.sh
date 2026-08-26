#!/usr/bin/env bash
# 선착순 접수 공정성 측정용 클러스터.
#
# 이 설계에서 DB 가 하는 일은 재고 수(<=1,000)로 묶인다. 90만 요청이 와도 재고 차감은
# 1,000번뿐이고 나머지는 메모리 조회 후 409 다. 그래서 DB 를 크게 잡을 이유가 없고,
# 실제 부하는 Order 가 초당 3,000건의 409 를 내는 데 든다.
#
#   pool        machine         vCPU  파드                          요청
#   default     e2-highmem-2      2   Prometheus, Grafana, Loki      ~1.2
#   svc         e2-standard-8     8   service-order x2, product x1    4.5
#   db          e2-standard-8     8   mysql-order, mysql-product      4.0
#   kafka       e2-standard-2     2   kafka x3                        0.9
#   loadgen     e2-standard-8     8   k6                               --
#                                ----
#                                 28 (계정 상한 32, 여유 4)
#
# loadgen 이 8 vCPU 인 이유: e2-standard-4 는 2,500~3,000 rps 에서 dropped_iterations 를
# 냈고, 그러면 대상이 아니라 생성기를 재게 된다.
set -euo pipefail
CLUSTER="${1:-flash-fair}"
ZONE="${2:-asia-northeast3-a}"
COMMON="--zone $ZONE --disk-type pd-standard --image-type COS_CONTAINERD --no-enable-autoupgrade --no-enable-autorepair --metadata disable-legacy-endpoints=true"

echo "[provision] 쿼터 확인"
python3 - <<'PY'
import json, subprocess, sys
def q(cmd):
    return {x["metric"]: x for x in json.loads(subprocess.run(
        cmd, capture_output=True, text=True, check=True).stdout)["quotas"]}
quotas = q(["gcloud","compute","regions","describe","asia-northeast3","--format=json(quotas)"])
quotas.update({k: v for k, v in q(["gcloud","compute","project-info","describe","--format=json(quotas)"]).items()
               if k == "CPUS_ALL_REGIONS"})
fail = False
for metric, need in (("CPUS_ALL_REGIONS", 28), ("CPUS", 28), ("SSD_TOTAL_GB", 40), ("DISKS_TOTAL_GB", 220)):
    x = quotas.get(metric)
    if not x: continue
    free = x["limit"] - x["usage"]
    print(f"  {metric:<16} free={free:<8} need={need:<6} {'OK' if free >= need else 'INSUFFICIENT'}")
    fail |= free < need
if fail:
    print("  -> 떠 있는 클러스터나 고아 디스크를 먼저 지울 것", file=sys.stderr); sys.exit(1)
PY

echo "[provision] 클러스터 생성 $CLUSTER"
gcloud container clusters create "$CLUSTER" --zone "$ZONE" \
  --num-nodes 1 --machine-type e2-highmem-2 \
  --disk-type pd-standard --disk-size 60 --node-labels role=monitoring \
  --no-enable-autoupgrade --no-enable-autorepair \
  --no-enable-ip-alias --no-enable-master-authorized-networks \
  --metadata disable-legacy-endpoints=true --logging=NONE --monitoring=NONE

add_pool() { gcloud container node-pools create "$1" --cluster "$CLUSTER" $COMMON \
  --machine-type "$2" --num-nodes 1 --disk-size "${4:-30}" --node-labels role="$3"; }

add_pool svc      e2-standard-8 svc      30
add_pool db       e2-standard-8 db       60
add_pool kafka    e2-standard-2 kafka    40
add_pool loadgen  e2-standard-8 loadgen  30

gcloud container clusters get-credentials "$CLUSTER" --zone "$ZONE"
kubectl get nodes -L role
echo "[provision] done"
