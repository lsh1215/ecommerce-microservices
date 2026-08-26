#!/usr/bin/env bash
# Deterministic reset before a measured run: clear order/payment test data, restore
# stock, and recreate all Kafka topics (clears message backlog + consumer offsets).
# Product/customer seed is preserved. Run before every before/after and every repeat.
set -euo pipefail
PASS="${DB_PASS:-changeme}"
K() { kubectl -n ecommerce "$@"; }
mysql_order() { K exec -i mysql-order-0 -- mysql -u root -p"$PASS" "$@" 2>&1 | grep -iv insecure || true; }
mysql_pay()   { K exec -i mysql-payment-0 -- mysql -u root -p"$PASS" "$@" 2>&1 | grep -iv insecure || true; }
mysql_prod()  { K exec -i mysql-product-0 -- mysql -u root -p"$PASS" "$@" 2>&1 | grep -iv insecure || true; }

echo "[reset] order/payment/reservation tables"
mysql_order -e "SET FOREIGN_KEY_CHECKS=0; DELETE FROM ecommerce_order.order_item; DELETE FROM ecommerce_order.saga_instance; DELETE FROM ecommerce_order.orders; SET FOREIGN_KEY_CHECKS=1;"
mysql_pay   -e "SET FOREIGN_KEY_CHECKS=0; DELETE FROM ecommerce_payment.payment; SET FOREIGN_KEY_CHECKS=1;"
mysql_prod  -e "DELETE FROM ecommerce_product.stock_reservation; UPDATE ecommerce_product.product_variant SET stock_quantity=100000000;"

echo "[reset] recreate Kafka topics (clear backlog + offsets)"
TOPICS="order.created order.cancelled payment.requested payment.completed payment.failed product.stock-reserved product.stock-released stock.reservation.confirm.requested stock.reservation.release.requested stock.reservation.confirmed stock.reservation.released customer.registered"
for t in $TOPICS; do
  for name in "$t" "$t.DLT"; do
    K exec kafka-0 -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic "$name" >/dev/null 2>&1 || true
  done
done
sleep 8
bash "$(dirname "$0")/kafka-topic-init.sh" >/dev/null
echo "[reset] restarting services for clean subscribe"
K rollout restart deploy service-order service-product service-payment service-customer >/dev/null
for d in service-order service-payment service-product service-customer; do K rollout status deploy/$d --timeout=160s >/dev/null 2>&1 || true; done
echo "[reset] done. orders=$(mysql_order -N -e 'SELECT COUNT(*) FROM ecommerce_order.orders;')"
