#!/usr/bin/env bash
# Run a k6 scenario as an in-cluster Job on the isolated k6 node, push metrics to
# Prometheus remote-write (for the Grafana k6 dashboard), and capture the summary
# JSON + log + measure window + hardware fingerprint into <out-dir>.
#
# The env fingerprint (raw/env.json) is captured BEFORE the load starts, i.e. the
# exact node/pod spec the numbers were produced on. Without it a run cannot be
# compared to any other run — see docs/observability/loadtest-baseline-audit.md
# for the 2026-08-01 drift that made three blog posts non-comparable.
#
# Usage: run-k6-job.sh <scenario.js> <out-dir> [KEY=VAL ...]   # KEY=VAL become k6 __ENV
#
# Evidence collection is not optional. The run is gated on evidence-preflight.py
# before the load starts and captures dashboards immediately after the measure
# window closes. Both are protocol steps that the 2026-08-11 campaign skipped,
# which is why 43 runs produced numbers with no recoverable dashboard evidence.
# Set REQUIRE_MYSQL=false for phases where no MySQL is the SUT.
set -euo pipefail
SCEN="$1"; OUT="$2"; shift 2
BASENAME="$(basename "$SCEN")"
NAME="k6-$(basename "$SCEN" .js | tr '._' '-')-$(date +%s)"
mkdir -p "$OUT/logs"
mkdir -p "$OUT/raw"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# WARMUP=1 runs produce no evidence, so there is nothing for the gate to
# protect. Running it here anyway created a circular dependency: the gate
# requires http_server_requests_seconds_count, Micrometer only creates that
# meter on the first served request, and the warmup that would serve it was
# the thing being blocked. After a fresh rollout no run could start at all.
if [ "${WARMUP:-0}" != "1" ]; then
  python3 "$HERE/evidence-preflight.py" --require-mysql="${REQUIRE_MYSQL:-true}" || {
    echo "[run-k6-job] ABORT: evidence preflight failed — refusing to burn a run" >&2
    exit 1
  }
fi

# Record the k6 __ENV alongside the deployment fingerprint. Without it two
# arms that differ only by load-script parameters are indistinguishable in
# the evidence directory.
K6ENV_CSV="$(printf '%s,' "$@" | sed 's/,$//')"
python3 "$HERE/capture-env.py" "$OUT" --k6-env "$K6ENV_CSV" ||
  echo "[run-k6-job] WARNING: env capture failed" >&2
RWURL="http://prometheus-remote-write.monitoring.svc.cluster.local:9090/api/v1/write"

kubectl -n ecommerce delete configmap "$NAME-scen" --ignore-not-found >/dev/null
kubectl -n ecommerce create configmap "$NAME-scen" --from-file="$BASENAME=$SCEN" >/dev/null

# env entries (block-scalar-safe)
ENVB="            - name: K6_PROMETHEUS_RW_SERVER_URL
              value: '$RWURL'
            - name: K6_PROMETHEUS_RW_TREND_STATS
              value: 'avg,p(95),p(99)'
            - name: K6_PROMETHEUS_RW_PUSH_INTERVAL
              value: '5s'"
for kv in "$@"; do
  ENVB="$ENVB
            - name: ${kv%%=*}
              value: '${kv#*=}'"
done

MANIFEST=$(mktemp)
cat > "$MANIFEST" <<EOF
apiVersion: batch/v1
kind: Job
metadata:
  name: $NAME
  namespace: ecommerce
spec:
  backoffLimit: 0
  template:
    spec:
      nodeSelector:
        role: loadgen
      restartPolicy: Never
      containers:
        - name: k6
          image: grafana/k6:0.54.0
          command: ["sh", "-c"]
          args:
            - |
              k6 run -o experimental-prometheus-rw --summary-export=/tmp/s.json --summary-trend-stats='avg,min,med,max,p(95),p(99)' /scripts/$BASENAME
              echo ===K6SUMMARY===
              cat /tmp/s.json
              echo
              echo ===K6END===
          env:
$ENVB
          volumeMounts:
            - name: scen
              mountPath: /scripts
      volumes:
        - name: scen
          configMap:
            name: $NAME-scen
EOF
kubectl apply -f "$MANIFEST" >/dev/null
echo "[run-k6-job] $NAME started"
python3 -c "import time;print(int(time.time()*1000))" > "$OUT/logs/window-from.txt"
kubectl -n ecommerce wait --for=condition=complete "job/$NAME" --timeout=1200s 2>/dev/null \
  || kubectl -n ecommerce wait --for=condition=failed "job/$NAME" --timeout=5s 2>/dev/null || true
python3 -c "import time;print(int(time.time()*1000))" > "$OUT/logs/window-to.txt"

POD=$(kubectl -n ecommerce get pods -l job-name="$NAME" -o jsonpath='{.items[0].metadata.name}')
kubectl -n ecommerce logs "$POD" > "$OUT/logs/k6.log" 2>&1 || true
awk '/===K6SUMMARY===/{f=1;next} /===K6END===/{f=0} f' "$OUT/logs/k6.log" > "$OUT/logs/k6-summary.json" || true
kubectl -n ecommerce delete configmap "$NAME-scen" --ignore-not-found >/dev/null
rm -f "$MANIFEST"
echo "[run-k6-job] done -> $OUT/logs (summary $(wc -c < "$OUT/logs/k6-summary.json" 2>/dev/null || echo 0) bytes)"

# WARMUP=1 runs are deliberately discarded (protocol step 4), so they are exempt
# from the validity gate and from capture: a cold JVM legitimately produces
# dropped_iterations, and gating on it would block the very run that fixes it.
if [ "${WARMUP:-0}" = "1" ]; then
  echo "[run-k6-job] WARMUP run — validity gate and capture skipped by design"
  exit 0
fi

# Contamination gate 3 + 5: a run whose requests mostly failed is not a
# measurement of anything, and capturing dashboards for it manufactures
# evidence for a broken system. Checked before capture so the failure is
# attributed to the run, not to the renderer.
python3 - "$OUT/logs/k6-summary.json" <<'PYEOF' || exit 1
import json, os, sys

path = sys.argv[1]
try:
    m = json.load(open(path))["metrics"]
except Exception as e:
    sys.exit(f"[run-k6-job] ERROR: unreadable k6 summary ({e}) — run INVALID")


def val(metric, key, default=0.0):
    v = m.get(metric, {})
    return (v.get("values", v) or {}).get(key, default)


failed = val("http_req_failed", "value")
dropped = val("dropped_iterations", "count")
reqs = val("http_reqs", "count")
if reqs == 0:
    sys.exit("[run-k6-job] ERROR: 0 requests issued — run INVALID")
if failed > 0.01:
    sys.exit(f"[run-k6-job] ERROR: http_req_failed={failed:.1%} (>1%) — the SUT was "
             f"rejecting the load, not serving it. Run INVALID; fix the endpoint/"
             f"seed state and re-measure.")
# dropped_iterations has two very different causes and only one invalidates a
# run. If k6 pinned maxVUs while the server's waiting time exploded, the drops
# are a *consequence* of the SUT saturating — that is the knee, and the run is
# valid (values above the knee are censored, which the analysis must state).
# If VUs never reached the ceiling, the generator itself ran out and the number
# measured is the generator's.
if dropped > 0:
    vus_max = val("vus_max", "max")
    waiting = val("http_req_waiting", "avg")
    server_bound = vus_max >= 0.95 * float(os.environ.get("MAX_VUS", vus_max or 1)) and waiting > 200
    if not server_bound:
        sys.exit(f"[run-k6-job] ERROR: dropped_iterations={int(dropped)} with "
                 f"vus_max={vus_max:.0f} and server waiting={waiting:.0f}ms — the load "
                 f"generator ran out before the SUT did, so this measures the "
                 f"generator. Run INVALID.")
    print(f"[run-k6-job] NOTE: dropped_iterations={int(dropped)} with VUs pinned at "
          f"{vus_max:.0f} and server waiting={waiting:.0f}ms — SUT-bound (knee reached). "
          f"Run is valid; treat post-knee throughput as censored.")
print(f"[run-k6-job] validity OK: {reqs:.0f} reqs, failed={failed:.2%}, dropped=0")
PYEOF

# Contamination gate 4: CFS throttling. A container can sit at 73% average CPU
# and still be throttled in 99% of its 100ms quota periods, because the average
# hides the per-period bursts. When that happens the thread is stopped while
# holding a DB connection, so the symptom shows up as pool exhaustion and looks
# like a database problem. The 2026-08-12 read-path knee was exactly this, and
# flash-sale hit the same trap on the write path. A run measured above the
# throttle knee measures the CPU limit, not the code.
python3 - "$OUT" <<'PYEOF' || exit 1
import json, os, subprocess, sys, urllib.parse

out = sys.argv[1]
try:
    a = int(open(f"{out}/logs/window-from.txt").read()) // 1000
    b = int(open(f"{out}/logs/window-to.txt").read()) // 1000
except Exception:
    sys.exit(0)

pod = subprocess.run(["kubectl", "get", "pod", "-n", "monitoring", "-l", "app=prometheus",
                      "-o", "jsonpath={.items[0].metadata.name}"],
                     capture_output=True, text=True).stdout.strip()
if not pod:
    sys.exit(0)

expr = ('100*sum(rate(container_cpu_cfs_throttled_periods_total{namespace="ecommerce",'
        'pod=~"service-.*"}[1m]))/clamp_min(sum(rate(container_cpu_cfs_periods_total'
        '{namespace="ecommerce",pod=~"service-.*"}[1m])),0.001)')
url = ("http://localhost:9090/api/v1/query_range?" +
       urllib.parse.urlencode({"query": expr, "start": a, "end": b, "step": 15}))
raw = subprocess.run(["kubectl", "exec", "-n", "monitoring", pod, "--", "wget", "-qO-", url],
                     capture_output=True, text=True).stdout
try:
    vals = [float(v) for _, v in json.loads(raw)["data"]["result"][0]["values"]]
except Exception:
    print("[run-k6-job] WARN: throttle check unavailable")
    sys.exit(0)

peak = max(vals) if vals else 0.0
limit = float(os.environ.get("MAX_THROTTLE_PCT", "25"))
if peak > limit:
    print(f"[run-k6-job] WARN: CFS throttling peaked at {peak:.1f}% (> {limit:.0f}%). "
          f"This run measured the CPU limit, not the code. Valid only for locating "
          f"an SLO knee; do NOT publish its throughput as a system capacity.")
else:
    print(f"[run-k6-job] throttle OK: peak {peak:.1f}%")
PYEOF

# Protocol step 7 + machine half of step 8. Capturing here — while the window is
# still fresh and the cluster is still up — is the only way this cannot be
# forgotten. Teardown after the campaign makes the evidence unrecoverable.
PF_LOG=$(mktemp)
kubectl port-forward -n monitoring svc/grafana 3000:3000 >"$PF_LOG" 2>&1 &
PF_PID=$!
trap 'kill $PF_PID 2>/dev/null; rm -f "$PF_LOG"' EXIT
for _ in $(seq 1 20); do curl -sf http://localhost:3000/grafana/api/health >/dev/null && break; sleep 2; done

python3 "$HERE/capture-dashboards.py" --root "$OUT" --audit "$OUT/logs/dashboard-audit.json" || {
  echo "[run-k6-job] ERROR: dashboard capture failed for $OUT" >&2
  exit 1
}
SHOTS=$(find "$OUT/screenshots" -name '*.png' 2>/dev/null | wc -l | tr -d ' ')
if [ "$SHOTS" -eq 0 ]; then
  echo "[run-k6-job] ERROR: 0 dashboards captured for $OUT — the measure window holds" >&2
  echo "             no renderable series. The k6 numbers exist but cannot be" >&2
  echo "             evidenced. Treat this run as INVALID and re-measure." >&2
  exit 1
fi
echo "[run-k6-job] captured $SHOTS dashboard PNG(s) -> $OUT/screenshots"
