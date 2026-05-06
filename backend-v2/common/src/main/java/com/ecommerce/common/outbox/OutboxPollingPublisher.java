package com.ecommerce.common.outbox;

import jakarta.persistence.OptimisticLockException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outer poll loop: scan PENDING outbox rows, dispatch each to a
 * per-row publisher that owns its own transaction.
 *
 * <p>Critically the loop itself runs OUTSIDE a write transaction.
 * The previous shape (one {@code @Transactional} over the whole loop)
 * meant JPA's flush — and therefore any {@code OptimisticLockException}
 * — happened only after the loop returned, so the per-row try/catch
 * could never see it. Now {@link OutboxRowPublisher#publishOne(Long)}
 * runs in its own {@code REQUIRES_NEW} transaction, the lock collision
 * fires at that transaction's commit, and we can react row-by-row.
 *
 * <p>{@code @Transactional(propagation = SUPPORTS, readOnly = true)} on
 * the scan keeps the read inside the scheduler's own (lightweight) read
 * transaction without forcing one for write semantics.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPollingPublisher {

    private final OutboxEventRepository outboxRepository;
    private final OutboxRowPublisher outboxRowPublisher;

    static final int MAX_RETRIES = 5;

    @Scheduled(fixedDelay = 500)
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
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
                int newCount = event.getRetryCount() + 1;
                if (newCount >= MAX_RETRIES) {
                    // Single-digit-frequency event, alerted via LogQL:
                    //   count_over_time({app=~"service-.*"} |~ "outbox.retry.exhausted" [5m])
                    outboxRowPublisher.markFailed(outboxId, e.getMessage());
                    continue;
                }
                outboxRowPublisher.recordRetry(outboxId, e.getMessage());
                // Bail out of this poll cycle — backoff is implicit via
                // @Scheduled fixedDelay. Next cycle will try this row again.
                break;
            }
        }
    }
}
