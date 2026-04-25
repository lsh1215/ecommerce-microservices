#!/usr/bin/env bash
# Per-phase deployment harness for the LGTM observability sweep.
#
#   ./scripts/deploy-phase.sh phase0
#
# Assumes the LGTM monitoring stack is already running on the GCE VM
# (`monitoring` namespace untouched). Tears down the previous phase's
# `ecommerce` workloads, applies the observability overlay onto the
# phase worktree, builds + imports the four service images to k3s, and
# applies the phase's app + storage manifests.
#
# Side-effects on the worktree:
#   - copies entrypoint.sh, Dockerfile, application-common.yml,
#     common/build.gradle from main
#   - patches each service manifest with prometheus.io/* annotations
#   - merges OTEL/MANAGEMENT keys into base/configmap.yml
#
# After this script completes, the *only* manual step is verification —
# `./scripts/audit-all-dashboards.py` and the visual audit.

set -euo pipefail

if [ $# -lt 1 ]; then
  echo "usage: $0 <phaseN>"
  exit 1
fi

PHASE="$1"
WORKTREE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PHASE_DIR="${WORKTREE}/../ecommerce-microservices-worktrees/${PHASE}"
ZONE=asia-northeast3-a
VM=ecommerce-k3s
SVCS=(service-product service-order service-payment service-customer)

if [ ! -d "${PHASE_DIR}" ]; then
  echo "ERROR: ${PHASE_DIR} does not exist"
  exit 1
fi

echo "[phase] === deploying ${PHASE} ==="
echo "[phase] worktree: ${PHASE_DIR}"

# --- 0. Tear down existing ecommerce workloads (preserve monitoring ns) ---
echo "[phase] tearing down current ecommerce workloads"
gcloud compute ssh "${VM}" --zone="${ZONE}" --command='
  sudo kubectl -n ecommerce delete deploy,statefulset,svc,ingress --all --timeout=120s 2>&1 | tail -15
  sudo kubectl -n ecommerce delete pvc --all --timeout=60s 2>&1 | tail -5
'

# --- 1. Apply observability overlay onto worktree ---
echo "[phase] applying observability overlay"
"${WORKTREE}/scripts/apply-observability-overlay.sh" "${PHASE_DIR}"

# --- 2. Build images in the phase worktree ---
echo "[phase] building images for ${PHASE}"
cd "${PHASE_DIR}"
for svc in "${SVCS[@]}"; do
  echo "  building ecommerce/${svc}:${PHASE}"
  docker buildx build --platform linux/amd64 --build-arg "SERVICE_NAME=${svc}" \
    -t "ecommerce/${svc}:${PHASE}" \
    -t "ecommerce/${svc}:latest" \
    --load backend-v2/ 2>&1 | tail -3
done

# --- 3. Save + scp + import into k3s containerd ---
echo "[phase] importing images to ${VM}"
for svc in "${SVCS[@]}"; do
  tar=/tmp/${svc}-${PHASE}.tar
  docker save "ecommerce/${svc}:latest" > "${tar}"
  gcloud compute scp --zone="${ZONE}" "${tar}" "${VM}:~/" 2>&1 | tail -1
  gcloud compute ssh "${VM}" --zone="${ZONE}" --command="
    sudo k3s ctr images import ~/${svc}-${PHASE}.tar 2>&1 | tail -2
    rm ~/${svc}-${PHASE}.tar
  "
  rm "${tar}"
done

# --- 4. Apply phase k8s manifests (NOT monitoring — already running) ---
echo "[phase] applying phase ${PHASE} k8s manifests"
gcloud compute ssh "${VM}" --zone="${ZONE}" --command='mkdir -p /tmp/phase-k8s && rm -f /tmp/phase-k8s/*.yml'
gcloud compute scp --zone="${ZONE}" --recurse "${PHASE_DIR}/k8s/base" "${VM}:/tmp/phase-k8s/" 2>&1 | tail -1
gcloud compute scp --zone="${ZONE}" --recurse "${PHASE_DIR}/k8s/services" "${VM}:/tmp/phase-k8s/" 2>&1 | tail -1
[ -d "${PHASE_DIR}/k8s/ingress" ] && gcloud compute scp --zone="${ZONE}" --recurse "${PHASE_DIR}/k8s/ingress" "${VM}:/tmp/phase-k8s/" 2>&1 | tail -1

gcloud compute ssh "${VM}" --zone="${ZONE}" --command='
  echo "[apply] base"
  sudo kubectl apply -f /tmp/phase-k8s/base/
  echo "[apply] services"
  sudo kubectl apply -f /tmp/phase-k8s/services/
  if [ -d /tmp/phase-k8s/ingress ]; then
    echo "[apply] ingress"
    sudo kubectl apply -f /tmp/phase-k8s/ingress/
  fi
'

# --- 5. Wait for service rollouts ---
echo "[phase] waiting for services Ready"
gcloud compute ssh "${VM}" --zone="${ZONE}" --command='
  for svc in service-product service-order service-payment service-customer; do
    sudo kubectl -n ecommerce rollout status deploy/$svc --timeout=240s 2>&1 | tail -1
  done
  sudo kubectl -n ecommerce get pods
'

echo "[phase] === ${PHASE} deploy complete ==="
