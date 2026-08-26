#!/usr/bin/env bash
# Pre-create all event topics (base + .DLT) with RF3 + partitions. auto-create is disabled.
set -euo pipefail
KPOD="${1:-kafka-0}"; PARTS="${2:-6}"; RF="${3:-3}"
TOPICS="order.created order.cancelled payment.requested payment.completed payment.failed product.stock-reserved product.stock-released stock.reservation.confirm.requested stock.reservation.release.requested stock.reservation.confirmed stock.reservation.released customer.registered flash.reserve.requested flash.reserve.result flash.sale.sold-out"
for t in $TOPICS; do
  for name in "$t" "$t.DLT"; do
    kubectl -n ecommerce exec "$KPOD" -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
      --create --if-not-exists --topic "$name" --partitions "$PARTS" --replication-factor "$RF" >/dev/null 2>&1
  done
done
echo "topics: $(kubectl -n ecommerce exec "$KPOD" -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null | grep -vc '^__')"
