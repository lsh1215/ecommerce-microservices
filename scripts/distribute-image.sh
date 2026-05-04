#!/usr/bin/env bash
# Distribute a locally-built docker image to a k3s worker node by way of
# `docker save | gzip | gcloud scp | ctr import`. The cluster runs
# without a container registry, so this is the path of least resistance.
#
# Usage:
#   scripts/distribute-image.sh ecommerce/service-order:no-outbox  ecommerce-svc-order
#   scripts/distribute-image.sh ecommerce/service-payment:phase2   ecommerce-svc-payment
#
# Pre-flight check (do once per session):
#   gcloud compute ssh ecommerce-k3s --zone=asia-northeast3-a \
#     --command='sudo kubectl get pods -n ecommerce -o wide | grep service-'
# Then pass each candidate node here.

set -euo pipefail

ZONE="${GCLOUD_ZONE:-asia-northeast3-a}"
IMAGE="${1:?image required, e.g. ecommerce/service-order:no-outbox}"
NODE="${2:?node required, e.g. ecommerce-svc-order}"

SVC="${IMAGE%%:*}"
SVC="${SVC##*/}"
TAG="${IMAGE##*:}"
TARBALL="/tmp/${SVC}-${TAG}.tar.gz"

echo "==> [1/5] save+gzip ${IMAGE}"
docker save "${IMAGE}" | gzip > "${TARBALL}"
ls -lh "${TARBALL}"

echo "==> [2/5] scp ${TARBALL} -> ${NODE}:/tmp/"
gcloud compute scp "${TARBALL}" "${NODE}:/tmp/" --zone="${ZONE}" >/dev/null

echo "==> [3/5] ctr import on ${NODE}"
gcloud compute ssh "${NODE}" --zone="${ZONE}" --command="\
  sudo gunzip -c /tmp/$(basename ${TARBALL}) | sudo k3s ctr -n=k8s.io images import -" \
  | tail -3

echo "==> [4/5] verify image present in containerd on ${NODE}"
gcloud compute ssh "${NODE}" --zone="${ZONE}" --command="\
  sudo k3s crictl images | grep ${SVC} | grep ${TAG} || (echo 'IMPORT FAILED'; exit 1)"

echo "==> [5/5] cleanup tarball on ${NODE}"
gcloud compute ssh "${NODE}" --zone="${ZONE}" --command="rm -f /tmp/$(basename ${TARBALL})"

echo "OK ${IMAGE} -> ${NODE}"
