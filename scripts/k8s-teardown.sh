#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S_DIR="$SCRIPT_DIR/../k8s"

echo "=== Removing ingress ==="
kubectl delete -f "$K8S_DIR/ingress/" --ignore-not-found

echo "=== Removing services ==="
kubectl delete -f "$K8S_DIR/services/" --ignore-not-found

echo "=== Removing base infrastructure ==="
kubectl delete -f "$K8S_DIR/base/" --ignore-not-found

echo "=== Removing namespace ==="
kubectl delete -f "$K8S_DIR/namespace.yml" --ignore-not-found

echo "=== Teardown complete ==="
