#!/usr/bin/env bash
# Outbox dual-write evidence runner. Executes one chaos leg end-to-end:
#   1. Roll out the leg's service-order image
#   2. Drop+create the order/payment DBs for a clean run
#   3. Warmup k6 (60s) — discarded
#   4. Steady baseline k6 (60s)
#   5. Chaos: kubectl scale sts kafka --replicas=0 + 60s of k6 load
#   6. Recovery: kubectl scale sts kafka --replicas=3 + 90s settle
#   7. Post-recovery k6 (30s)
#   8. Capture state.json (orders / payments / outbox row counts) into
#      docs/evidence/02-outbox-pattern/${LEG}/state.json
#
# Usage:
#   scripts/run-outbox-chaos-evidence.sh problem
#   scripts/run-outbox-chaos-evidence.sh solution
#   scripts/run-outbox-chaos-evidence.sh both
#
# Requires k6 in PATH and gcloud authenticated. Assumes:
#   - service-order:no-outbox already imported on the svc-order node
#   - service-order:phase2-obs already deployed and tagged on the cluster

set -euo pipefail

# Anchor everything to the repo root so the script works no matter where
# it is invoked from. k6 / docker / kubectl all need consistent cwd.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "${SCRIPT_DIR}")"
cd "${PROJECT_ROOT}"

ZONE="${GCLOUD_ZONE:-asia-northeast3-a}"
INGRESS="${INGRESS:-34.64.219.137}"
EVIDENCE_DIR="${PROJECT_ROOT}/docs/evidence/02-outbox-pattern"
K6_SCRIPT="${PROJECT_ROOT}/k6/scripts/order-flow.js"
LEG="${1:?leg required: problem | solution | both}"

leg_image() {
  case "$1" in
    problem)  echo "docker.io/ecommerce/service-order:no-outbox" ;;
    solution) echo "docker.io/ecommerce/service-order:phase2-obs" ;;
    *) echo "" ;;
  esac
}

leg_testid() {
  case "$1" in
    problem)  echo "02-dualwrite-problem" ;;
    solution) echo "02-dualwrite-solution" ;;
    *) echo "" ;;
  esac
}

ssh_k3s() { gcloud compute ssh ecommerce-k3s --zone="${ZONE}" --command="$1" 2>&1; }

mysql_exec() {
  # mysql_exec <db_pod> <database> <sql>
  # Password is fetched once into MYSQL_PWD env, never echoed.
  local pod=$1 db=$2 sql=$3
  ssh_k3s "set +x; PWD=\$(sudo kubectl get secret ecommerce-secrets -n ecommerce -o jsonpath='{.data.DB_ROOT_PASSWORD}' | base64 -d); sudo kubectl exec -n ecommerce -i ${pod} -- env MYSQL_PWD=\$PWD mysql -uroot -N -B ${db} -e \"${sql}\""
}

snapshot_counts() {
  # Echoes "orders payments pending published failed" on one line.
  local orders payments pending published failed
  orders=$(mysql_exec mysql-order-0 ecommerce_order "SELECT COUNT(*) FROM orders;" 2>/dev/null | tail -1 || echo 0)
  payments=$(mysql_exec mysql-payment-0 ecommerce_payment "SELECT COUNT(*) FROM payment;" 2>/dev/null | tail -1 || echo 0)
  pending=$(mysql_exec mysql-order-0 ecommerce_order "SELECT COUNT(*) FROM outbox_event WHERE status='PENDING';" 2>/dev/null | tail -1 || echo 0)
  published=$(mysql_exec mysql-order-0 ecommerce_order "SELECT COUNT(*) FROM outbox_event WHERE status='PUBLISHED';" 2>/dev/null | tail -1 || echo 0)
  failed=$(mysql_exec mysql-order-0 ecommerce_order "SELECT COUNT(*) FROM outbox_event WHERE status='FAILED';" 2>/dev/null | tail -1 || echo 0)
  for var in orders payments pending published failed; do
    if ! [[ "${!var}" =~ ^[0-9]+$ ]]; then eval "$var=0"; fi
  done
  echo "${orders} ${payments} ${pending} ${published} ${failed}"
}

# Counts how many records actually live in the order.created topic (across
# all 3 partitions) by reading from the earliest offset to the latest. This
# is the hardest-evidence number for "did the publish actually reach the
# broker" — independent of producer-side success metrics.
kafka_topic_count() {
  ssh_k3s "sudo kubectl exec -n ecommerce kafka-0 -- /bin/bash -c '\
    /opt/kafka/bin/kafka-get-offsets.sh \
      --bootstrap-server localhost:9092 \
      --topic order.created \
      --time -1 2>/dev/null \
      | awk -F: \"{ s+=\\\$3 } END { print s+0 }\"'" 2>/dev/null \
    | tail -1 | tr -dc '0-9'
}

deploy_leg_image() {
  local img=$1
  echo "==> swap service-order image -> ${img}"
  ssh_k3s "sudo kubectl set image deploy/service-order -n ecommerce service-order=${img}"
  echo "==> wait rollout"
  ssh_k3s "sudo kubectl rollout status deploy/service-order -n ecommerce --timeout=180s"
  # Brief settle so the new pod's HikariCP / KafkaTemplate are warm.
  sleep 8
}

run_k6_burst() {
  # 6 × 10s scenarios.smoke runs back-to-back to fill ~60s of wall clock.
  # Uses CLI --tag testid=… because the script's embedded scenarios block
  # ignores --vus/--duration.
  local testid=$1 secs=$2
  local iters=$(( secs / 10 ))
  for ((i=1; i<=iters; i++)); do
    k6 run --tag testid="${testid}" \
      --summary-mode=disabled \
      -e PRODUCT_API="http://${INGRESS}" \
      -e ORDER_API="http://${INGRESS}" \
      "${K6_SCRIPT}" \
      || true
  done
}

scale_kafka() {
  local n=$1
  echo "==> scale kafka StatefulSet -> replicas=${n}"
  ssh_k3s "sudo kubectl scale sts kafka -n ecommerce --replicas=${n}"
  if [[ "$n" -gt 0 ]]; then
    ssh_k3s "sudo kubectl rollout status sts kafka -n ecommerce --timeout=180s" || true
  fi
}

restart_payment_consumer() {
  if [[ "${SKIP_PAYMENT_RESTART:-0}" == "1" ]]; then
    echo "==> SKIP_PAYMENT_RESTART=1 — relying on existing service-payment pod"
    return 0
  fi
  echo "==> rollout restart service-payment (fresh kafka consumer metadata)"
  # condition=available is replicas-based and tolerant to OTel-agent slow boot;
  # avoids the 'pod failed liveness once → fail rollout' false negative.
  ssh_k3s "sudo kubectl rollout restart deploy/service-payment -n ecommerce && \
    sudo kubectl wait --for=condition=available deploy/service-payment -n ecommerce --timeout=300s"
  # Give the consumer 10s to subscribe before we start sending traffic.
  sleep 10
}

capture_state() {
  local leg=$1 chaos_start=$2 chaos_end=$3 image=$4
  local pre=$5 post_baseline=$6 post_chaos=$7 post_recovery=$8 final=$9
  local kafka_pre=${10} kafka_final=${11}
  local out_dir="${EVIDENCE_DIR}/${leg}"
  mkdir -p "${out_dir}"

  read pre_o pre_p pre_pe pre_pu pre_fa <<< "${pre}"
  read pb_o pb_p pb_pe pb_pu pb_fa <<< "${post_baseline}"
  read pc_o pc_p pc_pe pc_pu pc_fa <<< "${post_chaos}"
  read pr_o pr_p pr_pe pr_pu pr_fa <<< "${post_recovery}"
  read f_o f_p f_pe f_pu f_fa <<< "${final}"

  cat > "${out_dir}/state.json" <<JSON
{
  "leg": "${leg}",
  "testid": "$(leg_testid ${leg})",
  "image": "${image}",
  "chaos_kind": "kubectl scale sts kafka --replicas=0 -> 3",
  "chaos_start_unix": ${chaos_start},
  "chaos_end_unix": ${chaos_end},
  "chaos_window_seconds": $(( chaos_end - chaos_start )),
  "measurement": "5-stage snapshot (pre / post-baseline / post-chaos / post-recovery / final); deltas computed between adjacent stages so each phase's contribution is isolated",
  "snapshots": {
    "pre":            { "orders": ${pre_o}, "payments": ${pre_p}, "outbox_pending": ${pre_pe}, "outbox_published": ${pre_pu}, "outbox_failed": ${pre_fa} },
    "post_baseline":  { "orders": ${pb_o},  "payments": ${pb_p},  "outbox_pending": ${pb_pe},  "outbox_published": ${pb_pu},  "outbox_failed": ${pb_fa} },
    "post_chaos":     { "orders": ${pc_o},  "payments": ${pc_p},  "outbox_pending": ${pc_pe},  "outbox_published": ${pc_pu},  "outbox_failed": ${pc_fa} },
    "post_recovery":  { "orders": ${pr_o},  "payments": ${pr_p},  "outbox_pending": ${pr_pe},  "outbox_published": ${pr_pu},  "outbox_failed": ${pr_fa} },
    "final":          { "orders": ${f_o},   "payments": ${f_p},   "outbox_pending": ${f_pe},   "outbox_published": ${f_pu},   "outbox_failed": ${f_fa} }
  },
  "stage_deltas": {
    "baseline_orders":   $(( pb_o - pre_o )),
    "baseline_payments": $(( pb_p - pre_p )),
    "baseline_lost":     $(( (pb_o - pre_o) - (pb_p - pre_p) )),
    "chaos_orders":      $(( pc_o - pb_o )),
    "chaos_payments":    $(( pc_p - pb_p )),
    "chaos_lost":        $(( (pc_o - pb_o) - (pc_p - pb_p) )),
    "recovery_orders":   $(( pr_o - pc_o )),
    "recovery_payments": $(( pr_p - pc_p )),
    "post_orders":       $(( f_o - pr_o )),
    "post_payments":     $(( f_p - pr_p )),
    "post_lost":         $(( (f_o - pr_o) - (f_p - pr_p) ))
  },
  "totals": {
    "orders":                  $(( f_o - pre_o )),
    "payments":                $(( f_p - pre_p )),
    "lost_event_gap":          $(( (f_o - pre_o) - (f_p - pre_p) )),
    "outbox_published":        $(( f_pu - pre_pu )),
    "outbox_failed":           $(( f_fa - pre_fa )),
    "outbox_pending_at_end":   ${f_pe},
    "kafka_topic_messages_pre":   ${kafka_pre:-0},
    "kafka_topic_messages_final": ${kafka_final:-0},
    "kafka_topic_messages_delta": $(( ${kafka_final:-0} - ${kafka_pre:-0} ))
  },
  "ingress": "http://${INGRESS}"
}
JSON
  echo "wrote ${out_dir}/state.json"
  cat "${out_dir}/state.json"
}

run_leg() {
  local leg=$1
  local img=$(leg_image ${leg})
  echo "============================================================"
  echo " LEG: ${leg}  IMAGE: ${img}"
  echo "============================================================"

  deploy_leg_image "${img}"
  restart_payment_consumer

  echo "==> pre-snapshot"
  local pre kafka_pre
  pre=$(snapshot_counts)
  kafka_pre=$(kafka_topic_count)
  echo "    pre: ${pre}  kafka_topic=${kafka_pre}"

  echo "==> warmup 60s (discarded)"
  run_k6_burst "$(leg_testid ${leg})-warmup" 60

  echo "==> baseline 60s"
  run_k6_burst "$(leg_testid ${leg})-baseline" 60
  # Brief settle so consumer commits the last in-flight messages before we snap.
  sleep 5
  echo "==> post-baseline snapshot"
  local pb
  pb=$(snapshot_counts)
  echo "    post-baseline: ${pb}"

  local chaos_start chaos_end
  chaos_start=$(date +%s)
  scale_kafka 0
  echo "==> chaos load 60s (kafka scaled to 0)"
  run_k6_burst "$(leg_testid ${leg})-chaos" 60
  scale_kafka 3
  chaos_end=$(date +%s)
  echo "==> post-chaos snapshot"
  local pc
  pc=$(snapshot_counts)
  echo "    post-chaos: ${pc}"

  echo "==> recovery+drain 90s"
  sleep 90
  echo "==> post-recovery snapshot"
  local pr
  pr=$(snapshot_counts)
  echo "    post-recovery: ${pr}"

  echo "==> post 30s"
  run_k6_burst "$(leg_testid ${leg})-post" 30
  sleep 5
  echo "==> final snapshot"
  local final kafka_final
  final=$(snapshot_counts)
  kafka_final=$(kafka_topic_count)
  echo "    final: ${final}  kafka_topic=${kafka_final}"

  capture_state "${leg}" "${chaos_start}" "${chaos_end}" "${img}" \
    "${pre}" "${pb}" "${pc}" "${pr}" "${final}" "${kafka_pre}" "${kafka_final}"
}

main() {
  case "${LEG}" in
    problem)  run_leg problem ;;
    solution) run_leg solution ;;
    both)     run_leg problem; run_leg solution ;;
    *) echo "leg must be: problem | solution | both" >&2; exit 1 ;;
  esac
  echo "DONE"
}

main "$@"
