#!/usr/bin/env bash
# Fetch Grafana community dashboards by ID and package each as a
# ConfigMap manifest labelled `grafana_dashboard: "1"` so the in-cluster
# sidecar picks them up without a Grafana pod restart.
#
# Output: k8s/monitoring/dashboards/dashboard-<slug>.yml
#
# Size guard: each dashboard JSON must be under 900 KiB so the ConfigMap
# fits below the 1 MiB etcd limit with labels/metadata overhead.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$SCRIPT_DIR/../k8s/monitoring/dashboards"
mkdir -p "$OUT_DIR"

# Curated set: id  slug                       description
declare -a DASHBOARDS=(
  "1860   node-exporter-full         Node Exporter Full (node metrics)"
  "15661  kubernetes-views-pods      Kubernetes Pods overview"
  "10939  jvm-micrometer             JVM / Spring Boot Micrometer"
  "18941  kafka-exporter-overview    Kafka Exporter consumer lag + ISR"
  "7362   mysql-overview             MySQL Overview"
  "13639  logs-app                   Loki Logs / App"
  "19665  k6-prometheus              k6 Prometheus remote_write"
)

MAX_SIZE=900000

for line in "${DASHBOARDS[@]}"; do
  read -r id slug desc <<< "$line"
  json_url="https://grafana.com/api/dashboards/${id}/revisions/latest/download"
  tmp=$(mktemp)
  echo ">> fetching ${id} (${slug}) — ${desc}"
  curl -fsSL "$json_url" -o "$tmp"
  size=$(wc -c < "$tmp")
  if [ "$size" -gt "$MAX_SIZE" ]; then
    echo "   ! size ${size}B > ${MAX_SIZE}B — skipping (ConfigMap would exceed 1MiB)"
    rm -f "$tmp"
    continue
  fi
  # Emit a ConfigMap wrapping the JSON under `data.<slug>.json`.
  out="$OUT_DIR/dashboard-${slug}.yml"
  {
    printf 'apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: grafana-dashboard-%s\n  namespace: monitoring\n  labels:\n    grafana_dashboard: "1"\ndata:\n  %s.json: |\n' "$slug" "$slug"
    sed 's/^/    /' "$tmp"
  } > "$out"
  rm -f "$tmp"
  echo "   wrote $out (${size}B)"
done

echo "done. apply with: kubectl apply -f k8s/monitoring/dashboards/"
