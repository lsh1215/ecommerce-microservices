#!/usr/bin/env bash
# Applies the LGTM observability overlay on top of a phase worktree so
# it can be deployed with the same monitoring stack as `main`.
#
# Usage:
#   ./scripts/apply-observability-overlay.sh <path-to-phase-worktree>
#
# Example:
#   ./scripts/apply-observability-overlay.sh \
#     ../ecommerce-microservices-worktrees/phase5
#
# What it copies over (idempotent — re-runs are safe, cp overwrites):
#   - backend-v2/entrypoint.sh               (OTel agent gate)
#   - backend-v2/Dockerfile                  (OTel agent stage)
#   - backend-v2/common/src/main/resources/application-common.yml
#                                            (logback trace pattern + management)
#   - backend-v2/common/build.gradle         (micrometer-registry-prometheus dep)
#   - k8s/monitoring/                        (entire LGTM stack)
#   - scripts/fetch-dashboards.sh            (so phase can regen dashboards)
#
# What it patches in the target worktree:
#   - configmap.yml                          (MANAGEMENT_*, OTEL_*, APP_*)
#   - services/*.yml                         (prometheus.io/* annotations)
#
# Not copied (each phase keeps its own version):
#   - k8s/base/kafka-*.yml / mysql-*.yml     (phase-specific topology)
#   - k8s/services/*.yml container images / env (service logic differs)

set -euo pipefail

if [ $# -lt 1 ]; then
  echo "usage: $0 <phase-worktree-path>"
  exit 1
fi

TARGET="$1"
if [ ! -d "$TARGET/backend-v2" ] || [ ! -d "$TARGET/k8s" ]; then
  echo "ERROR: $TARGET does not look like an ecommerce-microservices worktree"
  exit 1
fi

MAIN="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "[overlay] source: $MAIN"
echo "[overlay] target: $TARGET"

# 1. Backend files (entrypoint, Dockerfile, common resources & deps)
cp -v "$MAIN/backend-v2/entrypoint.sh"  "$TARGET/backend-v2/entrypoint.sh"
cp -v "$MAIN/backend-v2/Dockerfile"     "$TARGET/backend-v2/Dockerfile"
cp -v "$MAIN/backend-v2/common/src/main/resources/application-common.yml" \
      "$TARGET/backend-v2/common/src/main/resources/application-common.yml"
cp -v "$MAIN/backend-v2/common/build.gradle" \
      "$TARGET/backend-v2/common/build.gradle"

# 2. Monitoring manifests (entirely net new in each phase)
mkdir -p "$TARGET/k8s/monitoring/dashboards"
cp -v "$MAIN/k8s/monitoring"/*.yml "$TARGET/k8s/monitoring/"
cp -v "$MAIN/k8s/monitoring/dashboards"/*.yml "$TARGET/k8s/monitoring/dashboards/"

# 3. Helper script
cp -v "$MAIN/scripts/fetch-dashboards.sh" "$TARGET/scripts/fetch-dashboards.sh"
chmod +x "$TARGET/scripts/fetch-dashboards.sh"

# 4. Patch services manifests with prometheus.io/scrape annotations. Each
#    phase's service files may have different structure; add annotations
#    idempotently via regex.
python3 - <<PY
import pathlib, re
ports = { "service-product":  "8081", "service-order":    "8082",
          "service-payment":  "8083", "service-customer": "8084" }
for svc, port in ports.items():
    p = pathlib.Path("$TARGET/k8s/services") / f"{svc}.yml"
    if not p.exists():
        print(f"[overlay] skip {svc}: no manifest in target")
        continue
    txt = p.read_text()
    if "prometheus.io/scrape" in txt:
        print(f"[overlay] {svc}: annotations already present")
        continue
    new = re.sub(
        r'(template:\n    metadata:\n)(      labels:)',
        (r'\1      annotations:\n'
         r'        prometheus.io/scrape: "true"\n'
         rf'        prometheus.io/port: "{port}"\n'
         r'        prometheus.io/path: "/actuator/prometheus"\n'
         r'\2'),
        txt, count=1)
    if new == txt:
        print(f"[overlay] {svc}: WARN — template: metadata: labels: pattern not found")
    else:
        p.write_text(new)
        print(f"[overlay] {svc}: annotated (port {port})")
PY

# 5. Configmap env merge. Append/replace the observability keys.
CM="$TARGET/k8s/base/configmap.yml"
if [ -f "$CM" ]; then
  python3 - <<PY
import pathlib, re, yaml
p = pathlib.Path("$CM")
doc = yaml.safe_load(p.read_text())
if doc is None or doc.get("kind") != "ConfigMap":
    print("[overlay] $CM not a ConfigMap; skip")
else:
    data = doc.setdefault("data", {})
    want = {
      "SPRING_PROFILES_ACTIVE": data.get("SPRING_PROFILES_ACTIVE", "k8s"),
      "MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE":
        "health,info,prometheus,metrics,circuitbreakers,circuitbreakerevents",
      "MANAGEMENT_ENDPOINT_PROMETHEUS_ENABLED": "true",
    }
    for k, v in want.items():
        data[k] = v
    p.write_text(yaml.safe_dump(doc, sort_keys=False))
    print(f"[overlay] $CM: keys merged")
PY
fi

echo ""
echo "[overlay] DONE. Next steps in $TARGET:"
echo "  git -C $TARGET status      # review the overlay diff"
echo "  git -C $TARGET commit -am 'feat(observability): apply LGTM overlay from main'"
echo "  # then build + deploy via the usual flow"
