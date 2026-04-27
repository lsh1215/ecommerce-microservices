#!/usr/bin/env bash
# Idempotent cluster restore — bookends every session.
# Restores main images, clears chaos env, ensures kafka is up.

set -euo pipefail
ZONE=asia-northeast3-a
VM=ecommerce-k3s

echo "[restore] returning cluster to main code with chaos cleared"
gcloud compute ssh "$VM" --zone="$ZONE" --command='
  sudo kubectl -n ecommerce set image deploy/service-order service-order=docker.io/ecommerce/service-order:latest
  sudo kubectl -n ecommerce set image deploy/service-payment service-payment=docker.io/ecommerce/service-payment:latest

  # Clear all chaos / idempotency env overrides via json-patch
  for d in service-order service-payment service-product; do
    sudo kubectl -n ecommerce get deploy $d -o json | python3 -c "
import json, sys
d = json.load(sys.stdin)
envs = d[\"spec\"][\"template\"][\"spec\"][\"containers\"][0].get(\"env\", [])
keep = [e for e in envs
        if not e[\"name\"].startswith(\"APP_CHAOS\")
        and not e[\"name\"].startswith(\"APPLICATION_IDEMPOTENCY\")
        and not e[\"name\"].startswith(\"APPLICATION_BUSINESS_IDEMPOTENCY\")
        and not e[\"name\"].startswith(\"JAVA_TOOL_OPTIONS\")]
d[\"spec\"][\"template\"][\"spec\"][\"containers\"][0][\"env\"] = keep
print(json.dumps(d))" | sudo kubectl apply -f - 2>&1 | tail -1
  done

  # Ensure kafka is scaled up
  sudo kubectl -n ecommerce scale deploy/kafka --replicas=1 2>&1 | tail -1
  sudo kubectl -n ecommerce rollout status deploy/kafka --timeout=120s 2>&1 | tail -1
  sudo kubectl -n ecommerce rollout status deploy/service-order --timeout=240s 2>&1 | tail -1
  sudo kubectl -n ecommerce rollout status deploy/service-payment --timeout=240s 2>&1 | tail -1
  sudo kubectl -n ecommerce rollout status deploy/service-product --timeout=240s 2>&1 | tail -1

  echo "[restore] final pod state:"
  sudo kubectl -n ecommerce get pods -l "app in (service-order,service-payment,service-product,service-customer,kafka)" \
    -o jsonpath="{range .items[*]}{.metadata.name}{\"\\t\"}{.spec.containers[0].image}{\"\\n\"}{end}"
' 2>&1 | tail -10
