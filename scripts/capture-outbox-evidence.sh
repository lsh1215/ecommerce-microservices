#!/usr/bin/env bash
# After run-outbox-chaos-evidence.sh has produced state.json for both
# legs, this script captures the supporting evidence:
#   - dashboards/ecommerce-dualwrite-flow.png   (Grafana, time-bounded)
#   - logs/no-outbox-event-lost.log             (problem only)
#   - logs/outbox-retries.log                   (solution only)
#   - logs/loki-retry-exhausted.txt             (solution only)
#   - traces/example-trace.json                 (one orderNumber's full Tempo trace)
#   - traces/example-trace-summary.txt          (human-readable: which spans exist)
#
# Usage:
#   scripts/capture-outbox-evidence.sh problem
#   scripts/capture-outbox-evidence.sh solution
#   scripts/capture-outbox-evidence.sh both

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "${SCRIPT_DIR}")"
cd "${PROJECT_ROOT}"

ZONE="${GCLOUD_ZONE:-asia-northeast3-a}"
INGRESS="${INGRESS:-34.64.219.137}"
EVIDENCE_DIR="${PROJECT_ROOT}/docs/evidence/02-outbox-pattern"
DASHBOARD_UID="${DASHBOARD_UID:-ecommerce-dualwrite-flow}"
LEG="${1:?leg required: problem | solution | both}"

ssh_k3s() { gcloud compute ssh ecommerce-k3s --zone="${ZONE}" --command="$1" 2>&1; }

loki_query() {
  # loki_query <logql> <from-unix> <to-unix> <limit>
  local q=$1 fr=$2 to=$3 lim=${4:-200}
  local q_enc
  q_enc=$(python3 -c "import urllib.parse, sys; print(urllib.parse.quote(sys.argv[1]))" "$q")
  ssh_k3s "sudo kubectl exec -n monitoring deploy/loki -- wget -qO- \
    'http://localhost:3100/loki/api/v1/query_range?query=${q_enc}&start=${fr}000000000&end=${to}000000000&limit=${lim}'"
}

extract_log_lines() {
  # Reads loki query response on stdin, prints sorted-by-time log lines.
  python3 -c "
import sys, json
r = json.load(sys.stdin)
rows = []
for s in r.get('data', {}).get('result', []):
    for ts, line in s.get('values', []):
        rows.append((int(ts), line.strip()))
rows.sort()
for _, line in rows:
    print(' '.join(line.split()))
"
}

capture_dashboard_png() {
  local leg=$1 cs=$2 ce=$3
  # Pad window to include warmup + recovery + post.
  local from=$(( cs - 240 )) to=$(( ce + 240 ))
  local out="${EVIDENCE_DIR}/${leg}/dashboards/ecommerce-dualwrite-flow.png"
  mkdir -p "$(dirname "${out}")"
  echo "==> [${leg}] dashboard PNG (window=${from}..${to})"
  node "${PROJECT_ROOT}/scripts/capture-grafana-dashboard.mjs" \
    --uid "${DASHBOARD_UID}" --from "${from}" --to "${to}" --out "${out}"
}

capture_problem_logs() {
  local leg=$1 cs=$2 ce=$3
  local from=$(( cs - 240 )) to=$(( ce + 240 ))
  local out="${EVIDENCE_DIR}/${leg}/logs/no-outbox-event-lost.log"
  mkdir -p "$(dirname "${out}")"
  echo "==> [${leg}] no-outbox-event-lost.log (Loki time-filtered)"
  loki_query '{app="service-order"} |~ "event lost"' "${from}" "${to}" 5000 \
    | extract_log_lines > "${out}"
  echo "    wrote $(wc -l < "${out}") lines"
}

capture_solution_logs() {
  local leg=$1 cs=$2 ce=$3
  local from=$(( cs - 240 )) to=$(( ce + 240 ))
  local out_retries="${EVIDENCE_DIR}/${leg}/logs/outbox-retries.log"
  local out_exhausted="${EVIDENCE_DIR}/${leg}/logs/loki-retry-exhausted.txt"
  mkdir -p "$(dirname "${out_retries}")"

  echo "==> [${leg}] outbox-retries.log"
  loki_query '{app="service-order"} |~ "Outbox event publish failed"' "${from}" "${to}" 5000 \
    | extract_log_lines > "${out_retries}"
  echo "    wrote $(wc -l < "${out_retries}") lines"

  echo "==> [${leg}] loki-retry-exhausted.txt"
  loki_query '{app="service-order"} |~ "outbox.retry.exhausted"' "${from}" "${to}" 200 \
    | extract_log_lines > "${out_exhausted}"
  echo "    wrote $(wc -l < "${out_exhausted}") entries"
}

capture_one_trace() {
  # Picks one orderNumber from a service-order log line, then asks Loki
  # "does this same orderNumber appear in service-payment's log stream?"
  # — that is the dual-write observable. Same business identifier (the
  # ULID order number) on both sides means the event crossed the
  # boundary; absent on the consumer side means it was lost.
  #
  # We use orderNumber (not the OTel trace_id) as the correlation key
  # because Spring Kafka's @KafkaListener does not propagate the trace
  # context across the broker on this stack — each side starts a fresh
  # trace. orderNumber is propagated inside the message payload itself
  # so it survives the boundary regardless of trace plumbing.
  local leg=$1 cs=$2 ce=$3 pattern=$4
  local from=$(( cs - 240 )) to=$(( ce + 240 ))
  local out_dir="${EVIDENCE_DIR}/${leg}/traces"
  mkdir -p "${out_dir}"

  echo "==> [${leg}] picking one orderNumber + traceId from service-order log"
  local pick
  pick=$(loki_query "{app=\"service-order\"} |~ \"${pattern}\"" "${from}" "${to}" 50 \
    | python3 -c "
import sys, json, re
r = json.load(sys.stdin)
for s in r.get('data', {}).get('result', []):
    for ts, line in s.get('values', []):
        m_o = re.search(r'orderNumber=([A-Z0-9]+)', line)
        m_t = re.search(r'traceId=([0-9a-f]+)', line)
        if m_o:
            tid = m_t.group(1) if m_t else ''
            print(m_o.group(1), tid, int(ts))
            sys.exit(0)
") || true
  if [[ -z "${pick:-}" ]]; then
    echo "    no matching service-order log line with orderNumber — skipping"
    return 0
  fi
  read order_num trace_id _ts <<< "${pick}"
  echo "    orderNumber=${order_num}  traceId=${trace_id:-<none>}"

  echo "==> [${leg}] querying Loki for this orderNumber across services"
  local order_lines payment_lines
  order_lines=$(loki_query "{app=\"service-order\"} |~ \"${order_num}\"" "${from}" "${to}" 200 \
    | extract_log_lines)
  payment_lines=$(loki_query "{app=\"service-payment\"} |~ \"${order_num}\"" "${from}" "${to}" 200 \
    | extract_log_lines)

  echo "${order_lines}" > "${out_dir}/service-order.log"
  echo "${payment_lines}" > "${out_dir}/service-payment.log"
  local order_n payment_n
  order_n=$(printf '%s\n' "${order_lines}" | grep -c . || true)
  payment_n=$(printf '%s\n' "${payment_lines}" | grep -c . || true)

  cat > "${out_dir}/example-trace-summary.txt" <<TXT
orderNumber: ${order_num}
traceId (service-order side): ${trace_id:-<none captured>}
window: ${from}..${to} (unix seconds)

Loki query for orderNumber=${order_num} across services:
  service-order:    ${order_n} log lines  → see service-order.log
  service-payment:  ${payment_n} log lines  → see service-payment.log

Interpretation:
$(if [[ "${payment_n}" -eq 0 ]]; then
  echo "  payment-side count is ZERO. service-order produced log lines that"
  echo "  carry this orderNumber (the order was committed and the publish was"
  echo "  attempted), but service-payment never logged this orderNumber at all."
  echo "  The event is gone — there is no consumer-side trace, no payment row,"
  echo "  no acknowledgement. This is the end-to-end signature of dual-write"
  echo "  loss for one specific business identifier."
else
  echo "  Both sides carry the same orderNumber. service-payment's log lines"
  echo "  prove the event crossed the boundary, was consumed, and processed."
  echo "  Compare the timestamps: in the solution leg, the gap between"
  echo "  service-order publish and service-payment consume tells you the"
  echo "  outbox poller delay (~500ms steady, several seconds during chaos)."
fi)

Bonus: open the same orderNumber in Grafana Explore for Loki:
  http://${INGRESS}/grafana/explore?left=%7B%22datasource%22:%22loki%22,%22queries%22:%5B%7B%22expr%22:%22%7Bapp%3D~%5C%22service-.*%5C%22%7D%20%7C%3D%20%5C%22${order_num}%5C%22%22%7D%5D%7D
TXT

  cat "${out_dir}/example-trace-summary.txt"
}

capture_one_leg() {
  local leg=$1
  local state="${EVIDENCE_DIR}/${leg}/state.json"
  if [[ ! -f "${state}" ]]; then
    echo "ERROR: ${state} missing — run scripts/run-outbox-chaos-evidence.sh ${leg} first" >&2
    return 1
  fi

  local cs ce
  cs=$(python3 -c "import json; print(json.load(open('${state}'))['chaos_start_unix'])")
  ce=$(python3 -c "import json; print(json.load(open('${state}'))['chaos_end_unix'])")

  capture_dashboard_png "${leg}" "${cs}" "${ce}"

  if [[ "${leg}" == "problem" ]]; then
    capture_problem_logs "${leg}" "${cs}" "${ce}"
    capture_one_trace "${leg}" "${cs}" "${ce}" "event lost"
  else
    capture_solution_logs "${leg}" "${cs}" "${ce}"
    # Solution leg: any successfully-published Outbox event log carries
    # both orderNumber (we add it via partition key) and traceId.
    # "Outbox event published" matches the publishOne success line.
    # Solution leg: pick a SAGA completion log line — emitted only when
    # the order.created event was successfully published, consumed by
    # service-payment, processed, and the payment.completed echo arrived
    # back at service-order. That single line proves the full round-trip.
    capture_one_trace "${leg}" "${cs}" "${ce}" "SAGA 완료"
  fi

  echo "==> [${leg}] OK"
  ls -la "${EVIDENCE_DIR}/${leg}/dashboards/" "${EVIDENCE_DIR}/${leg}/logs/" "${EVIDENCE_DIR}/${leg}/traces/" 2>/dev/null
}

case "${LEG}" in
  problem)  capture_one_leg problem ;;
  solution) capture_one_leg solution ;;
  both)     capture_one_leg problem; capture_one_leg solution ;;
  *) echo "leg must be: problem | solution | both" >&2; exit 1 ;;
esac
