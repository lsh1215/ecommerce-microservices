#!/usr/bin/env bash
# Idempotent teardown of the evidence cluster + orphaned disks (credit-binding).
# Usage: teardown-cluster.sh [CLUSTER] [ZONE]
set -uo pipefail
CLUSTER="${1:-ecommerce-evidence}"
ZONE="${2:-asia-northeast3-a}"
REGION="${ZONE%-*}"

echo "[teardown] deleting cluster $CLUSTER ($ZONE) ..."
gcloud container clusters delete "$CLUSTER" --zone "$ZONE" --quiet 2>&1 | tail -3 || true

echo "[teardown] deleting orphaned gce-* / pvc-* PDs in $ZONE ..."
for d in $(gcloud compute disks list --filter="zone:($ZONE)" --format="value(name)" 2>/dev/null | grep -E "^(gke-|pvc-)" || true); do
  gcloud compute disks delete "$d" --zone "$ZONE" --quiet 2>&1 | tail -1 || true
done

echo "[teardown] verify zero resources"
echo "clusters: $(gcloud container clusters list --format='value(name)' 2>/dev/null | wc -l | tr -d ' ')"
echo "instances: $(gcloud compute instances list --format='value(name)' 2>/dev/null | wc -l | tr -d ' ')"
echo "disks: $(gcloud compute disks list --format='value(name)' 2>/dev/null | wc -l | tr -d ' ')"
echo "region CPUS usage: $(gcloud compute regions describe "$REGION" --format='json(quotas)' 2>/dev/null | jq -r '.quotas[] | select(.metric=="CPUS") | .usage')"
echo "[teardown] done. AR images retained (delete separately if desired)."
