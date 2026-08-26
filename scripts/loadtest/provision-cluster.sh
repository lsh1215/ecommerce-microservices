#!/usr/bin/env bash
# Provision the role-isolated evidence cluster.
#
# Node isolation prevents one workload's CPU/IO from contaminating another's
# latency. This is not a cost preference — it is the only thing that makes
# numbers from different phases comparable. The 2026-08-01 regression (all
# workloads collapsed onto one e2-standard-8 and service-product silently
# given 8 vCPU) is what this layout exists to prevent; see
# docs/observability/loadtest-baseline-audit.md.
#
# Two invariants every pool below obeys:
#   1. Every pod runs Guaranteed QoS (request == limit in k8s/). A pool must
#      therefore have enough allocatable CPU for the sum of its pods' requests.
#   2. Throughput headroom comes from pod scale-out, never from raising a pod's
#      limit. svc-product has 3 nodes so service-product can run 1..3 replicas
#      at a fixed 1500m each, one per node, with no noisy-neighbour coupling.
#
# Budget: this account's CPUS_ALL_REGIONS quota is 32 and free-trial accounts
# cannot request an increase. 28 leaves 4 vCPU of slack; an earlier 12-node /
# 32 vCPU draft sat exactly on the ceiling and would have failed mid-creation.
# Guaranteed QoS is what makes the smaller footprint safe: a co-located pod is
# hard-capped at its own limit, so sharing a node no longer means sharing CPU.
#
# vCPU ledger (allocatable is roughly machine vCPU minus ~70m kube-system):
#
#   pool         machine         n   vCPU  pods (all Guaranteed)         requests
#   default      e2-highmem-2    1     2   LGTM, Traefik, exporters x4    ~1.2
#   db-product   e2-standard-8   1     8   mysql-product                   6.0
#   db-replica   e2-standard-2   1     2   mysql-product-replica           1.5
#   db-shared    e2-standard-2   1     2   mysql-order/payment/customer    1.5
#   kafka        e2-standard-2   1     2   kafka x3                        1.5
#   svc-product  e2-standard-2   3     6   service-product x1..3      1.5 / node
#   svc-order    e2-standard-2   1     2   service-order                   1.5
#   svc-misc     e2-standard-2   1     2   service-payment + customer      1.3
#   loadgen      e2-standard-2   1     2   k6 job                            --
#                                    ----
#                                     28 vCPU across 10 nodes
#
# Disk placement. Two different quotas are in play and they must not fight:
#
#   pd-balanced -> regional SSD_TOTAL_GB quota (default 500GB, small)
#   pd-standard -> regional DISKS_TOTAL_GB quota (default 4096GB, large)
#
# The reservation benchmarks measure InnoDB row-lock hold time and commit
# fsync, so MySQL/Kafka/Redis PVCs must stay on pd-balanced — putting them on
# pd-standard changes the exact latency under measurement. Node boot disks
# carry no workload data (images are read once), so they go on pd-standard and
# stop competing for the SSD quota the databases need.
#
# This also closes a silent drift: k8s/base/* used to pin
# storageClassName: standard (pd-standard) while the live cluster's PVCs were
# standard-rwo (pd-balanced) — deploying from git gave a slower disk than the
# one the published numbers were measured on.
#
# Usage: provision-cluster.sh [CLUSTER] [ZONE]
set -euo pipefail
CLUSTER="${1:-ecommerce-evidence}"
ZONE="${2:-asia-northeast3-a}"
BOOT_DISK_TYPE=pd-standard
DATA_SSD_GB=40    # DB + Kafka + Redis PVCs, all pd-balanced

COMMON="--zone $ZONE --disk-type $BOOT_DISK_TYPE --image-type COS_CONTAINERD --no-enable-autoupgrade --no-enable-autorepair --metadata disable-legacy-endpoints=true"

echo "[provision] quota preflight"
python3 - "$DATA_SSD_GB" <<'PY'
import json, subprocess, sys
need = int(sys.argv[1])
out = subprocess.run(
    ["gcloud", "compute", "regions", "describe", "asia-northeast3", "--format=json(quotas)"],
    capture_output=True, text=True, check=True).stdout
quotas = {q["metric"]: q for q in json.loads(out)["quotas"]}

# CPUS_ALL_REGIONS is a project-wide cap and is NOT visible in the regional
# quota list. asia-northeast3 reports CPUS=100 while the account ceiling is 32.
gout = subprocess.run(["gcloud", "compute", "project-info", "describe", "--format=json(quotas)"],
                      capture_output=True, text=True, check=True).stdout
quotas.update({q["metric"]: q for q in json.loads(gout)["quotas"]
               if q["metric"] == "CPUS_ALL_REGIONS"})
fail = False
for metric, required in (("CPUS_ALL_REGIONS", 28), ("CPUS", 28), ("SSD_TOTAL_GB", need), ("DISKS_TOTAL_GB", 340)):
    q = quotas.get(metric)
    if not q:
        continue
    free = q["limit"] - q["usage"]
    state = "OK" if free >= required else "INSUFFICIENT"
    print(f"  {metric:<14} usage={q['usage']:<7} limit={q['limit']:<7} free={free:<7} need={required:<6} {state}")
    fail |= free < required
if fail:
    print("  -> free stale PVCs/disks or raise the quota before provisioning", file=sys.stderr)
    sys.exit(1)
PY

echo "[provision] creating cluster $CLUSTER (monitoring pool = default) ..."
gcloud container clusters create "$CLUSTER" \
  --zone "$ZONE" \
  --num-nodes 1 \
  --machine-type e2-highmem-2 \
  --disk-type "$BOOT_DISK_TYPE" --disk-size 60 \
  --node-labels role=monitoring \
  --no-enable-autoupgrade --no-enable-autorepair \
  --no-enable-ip-alias --no-enable-master-authorized-networks \
  --metadata disable-legacy-endpoints=true \
  --logging=NONE --monitoring=NONE

add_pool() { # name machine-type role disk [num-nodes]
  gcloud container node-pools create "$1" --cluster "$CLUSTER" $COMMON \
    --machine-type "$2" --num-nodes "${5:-1}" --disk-size "${4:-40}" \
    --node-labels role="$3"
}

# Live ledger as measured. The earlier version of this file declared a layout
# the cluster never actually ran (svc-product e2-standard-2 x3, loadgen
# e2-standard-2); every REV1 number was produced on the layout below, so the
# script now reproduces what the evidence was recorded on.
add_pool db-product   e2-standard-8 db-product   60
add_pool db-replica   e2-standard-4 db-replica   30
add_pool db-shared    e2-standard-2 db-shared    30
add_pool kafka        e2-standard-2 kafka        40
# service-product scales 1->3 pods at a fixed 1500m Guaranteed each. They share
# one 8 vCPU node; Guaranteed QoS keeps them from stealing each other's shares,
# and 3 x 1500m = 4.5 of 7.91 allocatable leaves headroom.
add_pool svc-product  e2-standard-8 svc-product  30
add_pool svc-app      e2-standard-4 svc-app      30
# e2-standard-4 topped out near 2,500-3,000 rps and returned
# dropped_iterations at 6,000 rps, i.e. it measured itself rather than the SUT.
# 8 vCPU is required to reach the read-path knee.
add_pool loadgen      e2-standard-8 loadgen      30

echo "[provision] get-credentials"
gcloud container clusters get-credentials "$CLUSTER" --zone "$ZONE"
kubectl get nodes -L role
echo "[provision] done"
