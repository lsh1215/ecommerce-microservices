package com.ecommerce.common.outbox;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.persistence.OptimisticLockException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPollingPublisher {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> stringKafkaTemplate;
    private final ObservationRegistry observationRegistry;

    private static final int MAX_RETRIES = 5;

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);

        for (OutboxEvent event : events) {
            try {
                publishOne(event);
            } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
                log.info("이벤트 {} 이미 다른 인스턴스에서 발행됨, 건너뜀", event.getEventId());
                // continue — another instance won the row
            } catch (Exception e) {
                event.incrementRetryCount();
                if (event.getRetryCount() >= MAX_RETRIES) {
                    event.markFailed();
                    // Structured WARN log replaces a Counter — single-digit-frequency event,
                    // alerted via LogQL: count_over_time({app=~"service-.*"} |~ "outbox.retry.exhausted" [5m])
                    log.warn("outbox.retry.exhausted event_id={} event_type={} aggregate_type={} retry_count={} error={}",
                        event.getEventId(), event.getEventType(), event.getAggregateType(),
                        MAX_RETRIES, e.getMessage());
                    continue;
                }
                log.warn("Outbox event publish failed (retry {}/{}): eventId={}, error={}",
                    event.getRetryCount(), MAX_RETRIES, event.getEventId(), e.getMessage());
                break;
            }
        }
    }

    /**
     * Publish a single outbox row, wrapped in {@link Observation} so that one call
     * site emits Micrometer Timer (publish duration) + Tempo trace span +
     * traceId-tagged log line. {@code observeChecked} propagates the checked
     * exception while letting the Observation API tag the span/timer with
     * {@code error=...} — the timer's error-bucketed series is what panel #2
     * relies on to distinguish failed from successful publishes.
     *
     * <p>Tag policy: only {@code aggregate.type} (≈4 values: Order, Payment,
     * Customer, Product) is attached. {@code event.type} is intentionally NOT
     * tagged — it's a free-form Kafka topic name and would let series count
     * grow with every new domain event added to the codebase.
     */
    private void publishOne(OutboxEvent event) throws Exception {
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
    }
}
