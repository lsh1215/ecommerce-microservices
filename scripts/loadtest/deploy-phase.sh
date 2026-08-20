#!/usr/bin/env bash
# Deploy the ecommerce evidence stack for one phase.
# Usage: deploy-phase.sh <REPO> <TAG> [--infra-only|--services-only]
#   REPO = asia-northeast3-docker.pkg.dev/<project>/ecommerce
#   TAG  = phase image tag (e.g. saga-after)
set -euo pipefail
REPO="${1:?REPO required}"; TAG="${2:?TAG required}"; MODE="${3:-all}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"
K=kubectl
SERVICES=(order product payment customer)

OVERLAY=""
cleanup() { if [ -n "$OVERLAY" ]; then rm -rf "$OVERLAY"; fi; }
trap cleanup EXIT

deploy_infra() {
  echo "== namespaces =="
  $K apply -f k8s/namespace.yml
  $K apply -f k8s/monitoring/namespace.yml
  echo "== config + secrets + otel active =="
  $K apply -f k8s/base/configmap.yml -f k8s/base/secrets.yml
  $K apply -f k8s/monitoring/otel-config-active.yml
  echo "== monitoring stack =="
  for f in alloy prometheus loki tempo grafana; do $K apply -f "k8s/monitoring/$f.yml"; done
  $K apply -f k8s/monitoring/dashboards/ || true
  echo "== mysql + exporters =="
  for db in order product payment customer; do $K apply -f "k8s/base/mysql-$db-statefulset.yml"; done
  $K apply -f k8s/base/mysqld-exporters.yml
  echo "== kafka (3-broker in-node quorum) =="
  $K apply -f k8s/base/kafka-statefulset.yml
}

# Write the overlay that injects the phase image via the kustomize images
# transformer. The manifests themselves carry a registry-less placeholder, so a
# project id or tag never reaches git.
write_overlay() {
  {
    echo "apiVersion: kustomize.config.k8s.io/v1beta1"
    echo "kind: Kustomization"
    echo "resources:"
    echo "  - ../k8s/services"
    echo "images:"
    for s in "${SERVICES[@]}"; do
      echo "  - name: ecommerce/service-$s"
      echo "    newName: $REPO/service-$s"
      echo "    newTag: $TAG"
    done
  } > "$1/kustomization.yml"
}

# The images transformer is a silent no-op when a name does not match: a renamed
# or hand-edited placeholder yields a clean render that still deploys the wrong
# image. The previous sed-based injection failed exactly this way and shipped a
# stale pinned revision for a whole phase. Compare the rendered images against
# what was asked for and refuse to apply on any mismatch.
assert_images() {
  local rendered="$1" got expected
  got="$(printf '%s\n' "$rendered" | awk '$1 == "image:" { print $2 }' | sort)"
  expected="$(printf '%s\n' "${SERVICES[@]}" | sed "s|^|$REPO/service-|; s|$|:$TAG|" | sort)"
  if [ "$got" != "$expected" ]; then
    echo "ERROR: image injection did not produce the requested images; not applying." >&2
    echo "--- expected ---" >&2; printf '%s\n' "$expected" >&2
    echo "--- rendered ---" >&2; printf '%s\n' "$got" >&2
    return 1
  fi
}

deploy_services() {
  echo "== services (image -> $REPO/*:$TAG) =="
  # mktemp must run here, not inside a command substitution: a subshell
  # assignment would never reach the EXIT trap and the overlay would leak.
  OVERLAY="$(mktemp -d "$ROOT/.k8s-overlay.XXXXXX")"
  write_overlay "$OVERLAY"
  local rendered
  rendered="$($K kustomize "$OVERLAY")"
  assert_images "$rendered"
  printf '%s\n' "$rendered" | $K apply -f -
  echo "== ingress =="
  $K apply -f k8s/ingress/jwt-auth-middleware.yml -f k8s/ingress/ingress.yml || true
}

case "$MODE" in
  --infra-only) deploy_infra ;;
  --services-only) deploy_services ;;
  *) deploy_infra; deploy_services ;;
esac
echo "deploy-phase done: tag=$TAG mode=$MODE"
