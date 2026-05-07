package com.ecommerce.common.idempotency;

/**
 * Thrown by {@link InternalIdempotentExecutor} when the DB unique constraint on
 * {@code processed_event.event_id} fires, indicating the event was already processed
 * (concurrent duplicate). Caught by {@link IdempotentEventHandler} outside the
 * transaction boundary so Spring never sees an active rollback-only transaction.
 */
public class DuplicateEventException extends RuntimeException {

    public DuplicateEventException(String eventId) {
        super("Duplicate event detected: eventId=" + eventId);
    }
}
