#!/bin/sh
set -e

BOOTSTRAP="${BOOTSTRAP:-kafka-0.kafka-service.ecommerce.svc.cluster.local:9092}"
TOPICS="order.created order.cancelled payment.completed payment.failed product.stock-reserved product.stock-released customer.registered"

echo "Waiting for Kafka cluster to be ready..."
until /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server "$BOOTSTRAP" > /dev/null 2>&1; do
  echo "  Kafka not ready yet, retrying in 5s..."
  sleep 5
done
echo "Kafka cluster ready."

for TOPIC in $TOPICS; do
  echo "Creating topic: $TOPIC"
  /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$BOOTSTRAP" \
    --create \
    --if-not-exists \
    --topic "$TOPIC" \
    --partitions 3 \
    --replication-factor 3 \
    --config min.insync.replicas=2
done

echo "All topics created successfully."
