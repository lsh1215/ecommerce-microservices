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

# Pre-Phase-5 worktrees pin RandomGeneratorFactory.getDefault() which
# resolves to L32X64MixRandom — a class absent from many JRE 21 builds
# (Alpine especially, but also the eclipse-temurin variant we use).
# Main switched to ThreadLocalRandom; cherry-pick that file so payment
# starts cleanly under every phase.
PSP_REL=backend-v2/service-payment/src/main/java/com/ecommerce/payment/application/service/PaymentStubProcessor.java
if [ -f "$MAIN/$PSP_REL" ] && [ -f "$TARGET/$PSP_REL" ]; then
  cp -v "$MAIN/$PSP_REL" "$TARGET/$PSP_REL"
fi

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
    new = txt
    if ("prometheus.io/scrape" in txt and "imagePullPolicy" in txt
            and "otel-config" in txt):
        print(f"[overlay] {svc}: annotations + imagePullPolicy + otel-config already present")
        continue
    new = re.sub(
        r'(template:\n    metadata:\n)(      labels:)',
        (r'\1      annotations:\n'
         r'        prometheus.io/scrape: "true"\n'
         rf'        prometheus.io/port: "{port}"\n'
         r'        prometheus.io/path: "/actuator/prometheus"\n'
         r'\2'),
        new, count=1)
    # imagePullPolicy: IfNotPresent so k3s uses the locally-imported
    # `:latest` image instead of trying to pull from docker.io.
    if "imagePullPolicy" not in new:
        new = re.sub(
            r'(image: ecommerce/[a-z-]+:latest)\n',
            r'\1\n          imagePullPolicy: IfNotPresent\n',
            new, count=1)
    # otel-config envFrom — feeds OTEL_EXPORTER_OTLP_ENDPOINT etc. into the
    # container so the OTel Java agent attaches at startup. `optional: true`
    # keeps local k3d / docker compose runs working when the ConfigMap is
    # absent (entrypoint.sh treats unset endpoint as "agent inert").
    # Indentation matches the existing envFrom block: 12-space indent for
    # the list dash, 16-space for `name:` (the same column the existing
    # configMapRef / secretRef entries use).
    if "otel-config" not in new:
        new = re.sub(
            r'(envFrom:\n(?:            - [a-zA-Z]+:\n                name: [a-zA-Z-]+\n)+)',
            (r'\1            - configMapRef:\n'
             r'                name: otel-config\n'
             r'                optional: true\n'),
            new, count=1)
    if new != txt:
        p.write_text(new)
        print(f"[overlay] {svc}: patched (annotations / imagePullPolicy / otel-config)")
    else:
        print(f"[overlay] {svc}: nothing to change")
PY

# 5. Configmap env merge. Append/replace the observability keys.
CM="$TARGET/k8s/base/configmap.yml"
# Pre-Phase-5 ingress files declare ingressClassName: nginx — k3s on
# the GCE VM ships Traefik. Rewrite the class so the ingress takes effect.
for ing in "$TARGET"/k8s/ingress/*.yml; do
  [ -f "$ing" ] || continue
  python3 - "$ing" <<'PY'
import sys, yaml, pathlib
p = pathlib.Path(sys.argv[1])
docs = list(yaml.safe_load_all(p.read_text()))
changed = False
for d in docs:
    if isinstance(d, dict) and d.get("kind") == "Ingress":
        spec = d.setdefault("spec", {})
        if spec.get("ingressClassName") != "traefik":
            spec["ingressClassName"] = "traefik"
            changed = True
if changed:
    p.write_text(yaml.safe_dump_all(docs, sort_keys=False))
    print(f"[overlay] {p}: ingressClassName -> traefik")
PY
done

if [ -f "$CM" ]; then
  CM_PATH="$CM" python3 - <<'PY'
import os, pathlib, yaml
p = pathlib.Path(os.environ["CM_PATH"])
doc = yaml.safe_load(p.read_text())
if doc is None or doc.get("kind") != "ConfigMap":
    print(f"[overlay] {p} not a ConfigMap; skip")
else:
    data = doc.setdefault("data", {})
    # Schema bootstrap (validate would fail before tables exist),
    # actuator exposure, and the RestClient base URLs that
    # service-order reads via @Value-driven properties.
    want = {
        "SPRING_PROFILES_ACTIVE": data.get("SPRING_PROFILES_ACTIVE", "k8s"),
        "SPRING_JPA_HIBERNATE_DDL_AUTO": "update",
        "MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE":
            "health,info,prometheus,metrics,circuitbreakers,circuitbreakerevents",
        "MANAGEMENT_ENDPOINT_PROMETHEUS_ENABLED": "true",
        "APP_SERVICES_PRODUCT_URL": "http://service-product:8081",
        "APP_SERVICES_ORDER_URL": "http://service-order:8082",
        "APP_SERVICES_PAYMENT_URL": "http://service-payment:8083",
        "APP_SERVICES_CUSTOMER_URL": "http://service-customer:8084",
    }
    for k, v in want.items():
        data[k] = v
    p.write_text(yaml.safe_dump(doc, sort_keys=False))
    print(f"[overlay] {p}: keys merged ({len(want)} keys)")
PY
fi

echo ""
echo "[overlay] DONE. Next steps in $TARGET:"
echo "  git -C $TARGET status      # review the overlay diff"
echo "  git -C $TARGET commit -am 'feat(observability): apply LGTM overlay from main'"
echo "  # then build + deploy via the usual flow"
