package com.ecommerce.common.outbox;

import jakarta.persistence.OptimisticLockException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outer poll loop: scan PENDING outbox rows, dispatch each to a
 * per-row publisher that owns its own transaction.
 *
 * <p>Critically the loop itself runs OUTSIDE a write transaction. The
 * scheduled invocation arrives with no transactional context, the only
 * read here is {@code findTop100ByStatusOrderByCreatedAtAsc} (Spring Data
 * JPA opens its own short read transaction for that one query), and
 * the per-row write happens inside
 * {@link OutboxRowPublisher#publishOne(Long)}'s {@code REQUIRES_NEW}
 * transaction.
 *
 * <p>The previous shape (one {@code @Transactional} over the whole loop)
 * meant JPA's flush — and therefore any {@code OptimisticLockException} —
 * happened only after the loop returned, so the per-row try/catch could
 * never see it. With the per-row transaction in {@link OutboxRowPublisher},
 * the lock collision fires at that transaction's commit and we react
 * row-by-row.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPollingPublisher {

    private final OutboxEventRepository outboxRepository;
    private final OutboxRowPublisher outboxRowPublisher;

    @Scheduled(fixedDelay = 500)
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);

        for (OutboxEvent event : events) {
            Long outboxId = event.getId();
            String eventId = event.getEventId();
            try {
                outboxRowPublisher.publishOne(outboxId);
            } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
                // Another instance won the row at flush time. This is the
                // success signature of the @Version guard: exactly one
                // pod's UPDATE took effect, ours rolled back, no broker
                // duplicate from this side. Skip and continue to next row.
                log.info("이벤트 {} 이미 다른 인스턴스에서 발행됨, 건너뜀", eventId);
            } catch (Exception e) {
                handleRetryOrFail(outboxId, eventId, e);
            }
        }
    }

    /**
     * Atomically (per-row) decide retry vs markFailed in a fresh transaction.
     * Wrapped here in try/catch on optimistic-lock collisions because two
     * pollers can race on the same row's retryCount increment — losing one
     * increment is acceptable (the next poll cycle reconciles), but we don't
     * want the OptLock to bubble up and kill the whole batch.
     */
    private void handleRetryOrFail(Long outboxId, String eventId, Exception cause) {
        try {
            outboxRowPublisher.recordRetryOrMarkFailed(outboxId, cause.getMessage());
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException lockEx) {
            log.debug("retry-count update for eventId={} lost the race; next poll will retry", eventId);
        }
    }
}
