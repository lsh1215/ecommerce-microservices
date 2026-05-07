package com.ecommerce.common.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Public API for idempotent Kafka event processing.
 *
 * <p>This class is intentionally <strong>not</strong> {@code @Transactional}. The
 * transactional scope is owned entirely by {@link InternalIdempotentExecutor}, which is
 * injected as a Spring bean so the AOP proxy is effective (no self-invocation bypass).
 *
 * <p>Duplicate detection relies exclusively on the DB unique constraint on
 * {@code processed_event.event_id}. The {@link InternalIdempotentExecutor} inserts the
 * marker <em>before</em> running the processor, so both commit atomically. A concurrent
 * duplicate triggers a {@link DuplicateEventException} inside the (still-open but empty)
 * transaction; that exception is caught here — outside the tx boundary — which keeps
 * Spring's transaction infrastructure clean and prevents {@code UnexpectedRollbackException}.
 *
 * <p>{@code application.idempotency.enabled=false} bypasses dedup entirely (evidence
 * harness use only — never use in production).
 */
@Component
@Slf4j
public class IdempotentEventHandler {

    private final InternalIdempotentExecutor executor;
    private final boolean idempotencyEnabled;

    public IdempotentEventHandler(
            InternalIdempotentExecutor executor,
            @Value("${application.idempotency.enabled:true}") boolean idempotencyEnabled) {
        this.executor = executor;
        this.idempotencyEnabled = idempotencyEnabled;
    }

    /**
     * Processes {@code processor} exactly once per {@code eventId}.
     *
     * @return {@code true} if the processor was executed, {@code false} if the event was
     *         already recorded (duplicate skipped)
     */
    public boolean tryProcess(String eventId, String eventType, Runnable processor) {
        if (!idempotencyEnabled) {
            log.warn("idempotency disabled — running processor without dedup check: eventId={}", eventId);
            processor.run();
            return true;
        }

        try {
            executor.execute(eventId, eventType, processor);
            return true;
        } catch (DuplicateEventException e) {
            log.info("Duplicate event skipped: eventId={}, type={}", eventId, eventType);
            return false;
        }
    }
}
