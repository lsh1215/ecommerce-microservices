#!/usr/bin/env bash
# Fetch Grafana community dashboards by ID and package each as a
# ConfigMap manifest labelled `grafana_dashboard: "1"`. Grafana's sidecar
# picks up labelled ConfigMaps without restart.
#
# Post-processing:
#   - Substitute `${DS_PROMETHEUS}` and variant template vars with the
#     provisioned datasource UID `prometheus`.
#   - Substitute `${DS_LOKI}` with `loki`.
#   - Strip `__inputs` / `__requires` blocks so Grafana doesn't kick off
#     the import wizard for provisioned content.
#   - Set the dashboard's own `uid` so re-applies update in place.
# Size guard: reject any dashboard JSON exceeding 900 KiB (leaves room
# under the 1 MiB ConfigMap cap for annotations/labels overhead).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$SCRIPT_DIR/../k8s/monitoring/dashboards"
mkdir -p "$OUT_DIR"

# id  slug                     description
declare -a DASHBOARDS=(
  "15661  kubernetes-views-pods      Kubernetes Pods overview (cAdvisor-driven)"
  "4701   jvm-micrometer             JVM / Spring Boot Micrometer (classic)"
  "18941  kafka-exporter-overview    Kafka Exporter consumer lag + ISR"
  "7362   mysql-overview             MySQL Overview"
  "13639  logs-app                   Loki Logs / App"
  "19665  k6-prometheus              k6 Prometheus remote_write"
)

MAX_SIZE=900000

# Normalize the JSON: substitute DS_* vars, remove __inputs/__requires.
normalize_json() {
  local src="$1" dst="$2" slug="$3"
  python3 - "$src" "$dst" "$slug" <<'PY'
import json, re, sys
src, dst, slug = sys.argv[1], sys.argv[2], sys.argv[3]
with open(src) as f:
    txt = f.read()
# Rewrite every ${DS_*} reference to a known datasource UID. Every
# ${DS_*} whose name has LOKI in it → "loki"; everything else → "prometheus".
def ds_repl(match):
    var = match.group(1)
    return '"loki"' if "LOKI" in var else '"prometheus"'
txt = re.sub(r'"\$\{(DS_[A-Z0-9_]+)\}"', ds_repl, txt)
# Some panels embed the ref inline (not string-wrapped), eg "uid":${DS_X}
txt = re.sub(r'\$\{(DS_[A-Z0-9_]+)\}',
             lambda m: 'loki' if 'LOKI' in m.group(1) else 'prometheus', txt)
# Catch any residual bare DS_PROMETHEUS tokens that slipped through
# (some dashboards reference them in non-standard positions)
txt = re.sub(r'\bDS_[A-Z0-9_]+\b',
             lambda m: 'loki' if 'LOKI' in m.group(0) else 'prometheus', txt)
data = json.loads(txt)
data.pop('__inputs', None)
data.pop('__requires', None)
# Pin UID for idempotent re-provisioning
data['uid'] = f'ecommerce-{slug}'
data['id'] = None
data['editable'] = False
# Normalise every datasource spec to match our provisioned UIDs
def walk(x):
    if isinstance(x, dict):
        # Normalise every `datasource` field to the modern object form
        # Grafana 10+ expects: {"type": "<plugin>", "uid": "<uid>"}. The
        # legacy string form triggers "Failed to upgrade legacy queries".
        if 'datasource' in x:
            ds = x['datasource']
            if isinstance(ds, dict):
                t = ds.get('type', '')
                x['datasource'] = (
                    {'type': 'loki', 'uid': 'loki'} if t == 'loki'
                    else {'type': 'prometheus', 'uid': 'prometheus'}
                )
            elif isinstance(ds, str):
                low = ds.lower()
                x['datasource'] = (
                    {'type': 'loki', 'uid': 'loki'} if 'loki' in low
                    else {'type': 'prometheus', 'uid': 'prometheus'}
                )
            else:
                # null or missing — fill with default prometheus
                x['datasource'] = {'type': 'prometheus', 'uid': 'prometheus'}
        for v in x.values():
            walk(v)
    elif isinstance(x, list):
        for v in x:
            walk(v)
walk(data)
with open(dst, 'w') as f:
    json.dump(data, f, separators=(',', ':'))
PY
}

for line in "${DASHBOARDS[@]}"; do
  read -r id slug desc <<< "$line"
  json_url="https://grafana.com/api/dashboards/${id}/revisions/latest/download"
  raw=$(mktemp)
  norm=$(mktemp)
  echo ">> fetching ${id} (${slug}) — ${desc}"
  curl -fsSL "$json_url" -o "$raw"
  normalize_json "$raw" "$norm" "$slug"
  size=$(wc -c < "$norm")
  if [ "$size" -gt "$MAX_SIZE" ]; then
    echo "   ! size ${size}B > ${MAX_SIZE}B — skipping"
    rm -f "$raw" "$norm"
    continue
  fi
  out="$OUT_DIR/dashboard-${slug}.yml"
  {
    printf 'apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: grafana-dashboard-%s\n  namespace: monitoring\n  labels:\n    grafana_dashboard: "1"\ndata:\n  %s.json: |\n' "$slug" "$slug"
    sed 's/^/    /' "$norm"
  } > "$out"
  rm -f "$raw" "$norm"
  echo "   wrote $out (${size}B)"
done

echo "done. apply with: kubectl apply -f k8s/monitoring/dashboards/"
