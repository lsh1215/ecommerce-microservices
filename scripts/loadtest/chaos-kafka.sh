#!/usr/bin/env bash
# Kafka chaos helpers for the Outbox phase.
#   kill-broker <ordinal>  — delete one broker pod (HA test: ISR should stay >= minISR 2, writes continue)
#   down                   — scale Kafka to 0 (full outage: outbox retains events)
#   up                     — scale Kafka back to 3 (recovery: outbox drains, published == orders)
#   isr <topic>            — show partition leader/ISR/replicas for a topic
#   status                 — quorum + under-replicated partitions
set -euo pipefail
K() { kubectl -n ecommerce "$@"; }
KP="${KAFKA_POD:-kafka-0}"
cmd="${1:?usage: kill-broker <n> | down | up | isr <topic> | status}"; shift || true

case "$cmd" in
  kill-broker)
    n="${1:?ordinal}"; echo "[chaos] deleting kafka-$n (broker-process loss)"
    K delete pod "kafka-$n" --wait=false ;;
  down)
    echo "[chaos] scaling Kafka to 0 (full outage)"; K scale statefulset kafka --replicas=0 ;;
  up)
    echo "[chaos] scaling Kafka to 3 (recovery)"; K scale statefulset kafka --replicas=3
    K rollout status statefulset/kafka --timeout=180s ;;
  isr)
    t="${1:?topic}"
    K exec "$KP" -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic "$t" \
      2>/dev/null | grep -E 'Partition:' | awk '{print $2,$4,$6,$8,$10,$12}' ;;
  status)
    echo "-- quorum --"
    K exec "$KP" -- /opt/kafka/bin/kafka-metadata-quorum.sh --bootstrap-server localhost:9092 describe --status 2>/dev/null | grep -E 'CurrentVoters|LeaderId' || true
    echo "-- under-replicated partitions --"
    K exec "$KP" -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --under-replicated-partitions 2>/dev/null | sed -n '1,20p' || echo "(none = all in-sync)" ;;
  *) echo "unknown: $cmd" >&2; exit 2 ;;
esac
