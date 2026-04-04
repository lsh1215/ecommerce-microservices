#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S_DIR="$SCRIPT_DIR/../k8s"
BACKEND_DIR="$SCRIPT_DIR/../backend-v2"

echo "=== Building Docker images ==="
SERVICES=(service-product service-order service-payment service-customer)
for svc in "${SERVICES[@]}"; do
    echo "Building $svc..."
    docker build \
        --build-arg SERVICE_NAME="$svc" \
        -t "ecommerce/$svc:latest" \
        "$BACKEND_DIR"
done

echo "=== Creating namespace ==="
kubectl apply -f "$K8S_DIR/namespace.yml"

echo "=== Deploying base infrastructure ==="
kubectl apply -f "$K8S_DIR/base/"

echo "=== Waiting for MySQL to be ready ==="
kubectl -n ecommerce wait --for=condition=ready pod -l app=mysql --timeout=120s || true

echo "=== Waiting for Kafka to be ready ==="
kubectl -n ecommerce wait --for=condition=ready pod -l app=kafka --timeout=120s || true

echo "=== Deploying services ==="
kubectl apply -f "$K8S_DIR/services/"

echo "=== Deploying ingress ==="
kubectl apply -f "$K8S_DIR/ingress/"

echo "=== Deployment complete ==="
kubectl -n ecommerce get pods
