package com.ecommerce.common.outbox;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single-row outbox publisher, isolated in its own bean so that
 * Spring's transaction proxy actually wraps {@link #publishOne(Long)}.
 *
 * <p>Why a separate bean (and not a method on {@link OutboxPollingPublisher}):
 * the optimistic-lock race we want to surface is JPA's flush-time
 * {@code OptimisticLockException}. That exception is thrown when the
 * transaction commits, not at the call site. If the {@code @Transactional}
 * boundary covered the whole batch loop (the previous shape), the
 * exception would only fire after the loop ended and the per-row
 * try/catch could not catch it. With this bean, each call to
 * {@code publishOne} opens a {@link Propagation#REQUIRES_NEW} transaction
 * that commits per row, so the lock-collision propagates up to the
 * caller's try/catch as designed.
 *
 * <p>The entity is re-loaded inside this transaction (rather than passed
 * in as an argument from the outer scan) for two reasons:
 * (1) the outer scan's transaction has already committed by the time we
 *     get here, so the entity would be detached and {@code markPublished}
 *     wouldn't dirty-track;
 * (2) the re-load doubles as a status check — if another instance
 *     already moved the row to {@code PUBLISHED} in the gap between scan
 *     and publish, we skip the broker send entirely instead of relying
 *     on the lock collision to abort it.
 *
 * <p>Note on roles: the status pre-check and the @Version optimistic lock
 * are NOT redundant — they cover different windows. The pre-check skips
 * the broker send when another instance has already finished publishing
 * (a best-effort optimization). The optimistic lock guarantees
 * single-writer semantics when two instances both pass the pre-check
 * concurrently and reach flush — only one UPDATE succeeds.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRowPublisher {

    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> stringKafkaTemplate;
    private final ObservationRegistry observationRegistry;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOne(Long outboxId) throws Exception {
        OutboxEvent event = outboxRepository.findById(outboxId).orElse(null);
        if (event == null) {
            log.debug("Outbox row {} disappeared before publish — likely retention cleanup", outboxId);
            return;
        }
        if (event.getStatus() != OutboxEventStatus.PENDING) {
            // Another instance already published this row in the window between
            // the outer scan and this transaction start. Skip without logging
            // an OptLock collision — the race never actually reached flush.
            return;
        }

        Observation.createNotStarted("outbox.publish", observationRegistry)
            .lowCardinalityKeyValue("aggregate.type", event.getAggregateType())
            .observeChecked(() -> {
                stringKafkaTemplate.send(event.getEventType(), event.getPartitionKey(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);
                event.markPublished();
                log.debug("Outbox event published: {} (aggregate={}/{})",
                    event.getEventType(), event.getAggregateType(), event.getAggregateId());
                return null;
            });
        // Transaction commits here. If a concurrent poller incremented
        // version on the same row, JPA throws OptimisticLockException at
        // flush, this method's @Transactional rolls back, and the
        // exception propagates to the caller.
    }

    /**
     * Increment retryCount in a fresh transaction. If the count crosses
     * {@link #MAX_RETRIES} the row is marked {@code FAILED} in the same
     * transaction and an alert-friendly WARN log is emitted. Caller can
     * catch the resulting {@code OptimisticLockException} (rare; happens
     * only when two pollers race on the same row's increment) — the lost
     * increment is benign since the next poll cycle will retry.
     *
     * <p>Single-method API instead of separate {@code recordRetry} +
     * {@code markFailed} avoids a stale-counter race at the caller side
     * (caller's outer-loop {@code event.getRetryCount()} reflects scan-time
     * state and may be off-by-one if another instance already incremented).
     * The decision is made authoritatively here, against the freshly-loaded
     * row.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRetryOrMarkFailed(Long outboxId, String errorMessage) {
        outboxRepository.findById(outboxId).ifPresent(event -> {
            event.incrementRetryCount();
            if (event.getRetryCount() >= MAX_RETRIES) {
                event.markFailed();
                // Single-digit-frequency event, alerted via LogQL:
                //   count_over_time({app=~"service-.*"} |~ "outbox.retry.exhausted" [5m])
                log.warn("outbox.retry.exhausted event_id={} event_type={} aggregate_type={} retry_count={} error={}",
                    event.getEventId(), event.getEventType(), event.getAggregateType(),
                    MAX_RETRIES, errorMessage);
            } else {
                log.warn("Outbox event publish failed (retry {}/{}): eventId={}, error={}",
                    event.getRetryCount(), MAX_RETRIES, event.getEventId(), errorMessage);
            }
        });
    }
}
