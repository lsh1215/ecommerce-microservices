# Dashboard Verification Checklist

Use this checklist after deploying the saga, outbox, Kafka, circuit-breaker, and stock
reservation changes to a real environment. Local unit and integration tests verify code
paths, but the dashboards prove that runtime metrics, labels, exporters, and scrape
targets are wired correctly.

## Before Running Scenarios

- Confirm Prometheus targets for `service-order`, `service-product`, `service-payment`,
  Kafka exporter, and database exporter are up.
- Confirm the monitoring dashboard variables can select the deployed namespace and service.
- Confirm DLT topics exist for each consumed topic.
- Clear old test traffic from the selected time window or choose a fresh dashboard window.

## Order Saga

- Create an order that succeeds through stock reservation and payment.
- Verify order-service request rate and latency move during the order creation window.
- Verify saga completion logs appear for the same `orderNumber`.
- Verify no unexpected increase in compensation or failure panels.

## Stock Reservation

- Run several concurrent reservation attempts against a variant with limited stock.
- Verify product-service latency remains bounded under contention.
- Verify observed stock remains zero or positive in the database and product API response.
- Verify failed reservations surface as business failures, not service outages.

## Outbox

- Create order/payment events and verify `outbox_events_pending` rises briefly and drains.
- Verify pending outbox rows do not grow monotonically.
- During a controlled Kafka outage, verify pending rows accumulate instead of disappearing.
- After Kafka recovery, verify pending rows drain and failed rows remain explainable.

## Kafka Consumers

- Verify consumer lag rises only during load or controlled consumer downtime.
- Verify lag drains after consumers recover.
- Send or replay a malformed test record only in a controlled environment and verify it is
  routed to `<topic>.DLT`.
- Confirm DLT growth is treated as an incident signal, not a successful processing signal.

## Circuit Breaker

- Induce a Product 5xx or connection failure in a controlled environment.
- Verify the `productService` circuit breaker transitions through CLOSED, OPEN, and HALF_OPEN.
- Verify order-service fast-fail behavior while the circuit is OPEN.
- Verify Product 4xx business failures do not open the circuit.

## Pass Criteria

- Dashboards show the expected metric movements for each scenario.
- No panel stays empty because of a broken label, scrape target, or query.
- DLT, lag, outbox pending, and circuit-breaker panels are all tied to reproducible scenarios.
- Any unexpected metric gap becomes a dashboard/query fix before the related backend PR is merged.
