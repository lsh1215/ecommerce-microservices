package com.ecommerce.common.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal transactional executor for idempotent event processing.
 *
 * <p>Separated from {@link IdempotentEventHandler} so that the {@code @Transactional}
 * proxy wraps only this class; the outer handler catches {@link DuplicateEventException}
 * outside the transaction boundary, avoiding {@code UnexpectedRollbackException}.
 *
 * <p>Marker INSERT happens <em>before</em> the processor runs: if the INSERT succeeds,
 * we own this event and the processor is safe to execute. Both the marker and the
 * processor side-effects commit atomically. If the INSERT violates the unique constraint
 * (concurrent duplicate), we throw {@link DuplicateEventException} immediately and the
 * (empty) transaction rolls back cleanly — the processor never runs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InternalIdempotentExecutor {

    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void execute(String eventId, String eventType, Runnable processor) {
        try {
            processedEventRepository.saveAndFlush(ProcessedEvent.of(eventId, eventType));
        } catch (DataIntegrityViolationException e) {
            log.info("Concurrent duplicate event detected (DB constraint): eventId={}", eventId);
            throw new DuplicateEventException(eventId);
        }
        processor.run();
    }
}
