#!/usr/bin/env bash
# Bring up the full ecommerce stack in a local k3d (k3s-in-Docker) cluster.
# Purpose: reproduce the GCE k8s deployment locally — 3-broker Kafka StatefulSet,
# MySQL StatefulSet, 4 services, Traefik ingress — using the same manifests.
#
# For daily service development, use `./scripts/start.sh` (MySQL + Kafka only,
# services run via `./gradlew bootRun`). This script is for 3-broker scenario
# testing and deployment rehearsals.
#
# Prerequisites (one-time):
#   brew install k3d kubectl
#
# Usage:
#   ./scripts/local-k8s.sh           # create cluster (if missing) + deploy
#   k3d cluster delete ecommerce     # tear down when done

set -euo pipefail

CLUSTER_NAME="ecommerce"
HOST_HTTP_PORT="${HOST_HTTP_PORT:-8080}"
SERVICES=(service-product service-order service-payment service-customer)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BACKEND_DIR="$PROJECT_ROOT/backend-v2"
K8S_DIR="$PROJECT_ROOT/k8s"

command -v k3d >/dev/null     || { echo "k3d not installed. Run: brew install k3d";     exit 1; }
command -v kubectl >/dev/null || { echo "kubectl not installed. Run: brew install kubectl"; exit 1; }
command -v docker >/dev/null  || { echo "docker not installed.";                           exit 1; }

# 1. Create cluster (idempotent). Map host port so ingress is reachable on
#    http://localhost:$HOST_HTTP_PORT without sudo.
if k3d cluster list 2>/dev/null | awk 'NR>1 {print $1}' | grep -qx "$CLUSTER_NAME"; then
  echo "=== Cluster '$CLUSTER_NAME' exists, reusing ==="
else
  echo "=== Creating k3d cluster '$CLUSTER_NAME' (port $HOST_HTTP_PORT -> 80) ==="
  k3d cluster create "$CLUSTER_NAME" \
    --port "${HOST_HTTP_PORT}:80@loadbalancer" \
    --wait
fi
kubectl config use-context "k3d-$CLUSTER_NAME" >/dev/null

# 2. Build images (native platform — arm64 on Apple Silicon, amd64 on Intel).
echo "=== Building $(echo "${SERVICES[@]}" | wc -w | tr -d ' ') service images ==="
for svc in "${SERVICES[@]}"; do
  echo "--- $svc ---"
  docker build --build-arg "SERVICE_NAME=$svc" -t "ecommerce/${svc}:latest" "$BACKEND_DIR"
done

# 3. Import images into k3d nodes.
echo "=== Importing images into cluster ==="
k3d image import \
  "ecommerce/service-product:latest" \
  "ecommerce/service-order:latest" \
  "ecommerce/service-payment:latest" \
  "ecommerce/service-customer:latest" \
  -c "$CLUSTER_NAME"

# 4. Apply manifests in order.
echo "=== Applying manifests ==="
kubectl apply -f "$K8S_DIR/namespace.yml"
kubectl apply -f "$K8S_DIR/base/"

echo "=== Waiting for MySQL StatefulSet ==="
kubectl -n ecommerce rollout status statefulset/mysql --timeout=300s

echo "=== Waiting for Kafka 3-broker StatefulSet ==="
kubectl -n ecommerce rollout status statefulset/kafka --timeout=300s

kubectl apply -f "$K8S_DIR/services/"
kubectl apply -f "$K8S_DIR/ingress/"

echo "=== Waiting for services ==="
for svc in "${SERVICES[@]}"; do
  kubectl -n ecommerce rollout status "deploy/$svc" --timeout=300s
done

echo ""
echo "=== Pods ==="
kubectl -n ecommerce get pods
echo ""
echo "=== Access ==="
echo "  curl http://localhost:${HOST_HTTP_PORT}/api/products"
echo "  curl http://localhost:${HOST_HTTP_PORT}/api/brands"
echo ""
echo "Tear down: k3d cluster delete $CLUSTER_NAME"
