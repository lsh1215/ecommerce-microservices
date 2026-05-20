# Kafka Delivery Contract

## Scope

This project uses the transactional outbox pattern for cross-service domain events.
The delivery contract is **at-least-once**, not exactly-once.

The database transaction that changes local state also writes an `outbox_event` row.
`OutboxPollingPublisher` later asks `OutboxRowPublisher` to send each pending row to Kafka
in a separate transaction and marks the row as published only after the Kafka send succeeds.

## What This Guarantees

- Local aggregate changes and outbox row creation commit atomically in the same database transaction.
- A pending outbox row remains retryable when Kafka publish fails.
- Kafka producers use `acks=all`, retries, and idempotent producer settings to reduce broker-side duplicate writes during retry.
- Kafka consumers run with `enable.auto.commit=false`, so listener success, retry, and DLT recovery decide offset progress.
- Poison-pill records such as malformed JSON go to `<topic>.DLT` through `DefaultErrorHandler`.

## What This Does Not Guarantee

- It does not guarantee global exactly-once processing across database, Kafka, and downstream services.
- It does not prevent duplicate business effects when a consumer is not idempotent.
- It does not remove the need for unique business keys, processed-event records, or atomic status transitions in consumers.

## Required Consumer Rule

Every consumer that mutates state must be idempotent at the business boundary.
The minimum acceptable forms are:

- store and check the consumed event ID before applying side effects;
- enforce a unique business key such as `order_id` for payment creation;
- use an atomic state transition such as `RESERVED -> RELEASED` for compensation.

If a consumer cannot satisfy one of these rules, it is not compatible with this delivery contract.

## Operational Checks

- Check `outbox_events_pending`; a growing value means Kafka publish is delayed or blocked.
- Check consumer lag by group and topic; lag with stable outbox pending count means consumers are behind.
- Check `<topic>.DLT`; any non-empty DLT topic requires triage because those offsets have been recovered.
- Keep DLT topics pre-created for every consumed topic.

## References

- [Confluent: Exactly-once semantics are possible but require producer, broker, and consumer cooperation](https://www.confluent.io/blog/simplified-robust-exactly-one-semantics-in-kafka-2-5/)
- [Apache Kafka producer configuration: enable.idempotence](https://kafka.apache.org/documentation/#producerconfigs_enable.idempotence)
- [Spring Kafka reference: annotation error handling](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)
- [microservices.io: Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html)
