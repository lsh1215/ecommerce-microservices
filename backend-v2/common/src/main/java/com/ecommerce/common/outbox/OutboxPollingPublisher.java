package com.ecommerce.common.outbox;

import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPollingPublisher {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> stringKafkaTemplate;

    private static final int MAX_RETRIES = 5;

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (OutboxEvent event : events) {
            try {
                stringKafkaTemplate.send(event.getEventType(), event.getPartitionKey(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);
                event.markPublished();
                log.debug("Outbox event published: {} (aggregate={}/{})",
                    event.getEventType(), event.getAggregateType(), event.getAggregateId());
            } catch (Exception e) {
                event.incrementRetryCount();
                if (event.getRetryCount() >= MAX_RETRIES) {
                    event.markFailed();
                    log.error("Outbox event permanently failed after {} retries: eventId={}, type={}",
                        MAX_RETRIES, event.getEventId(), event.getEventType());
                    continue;
                }
                log.warn("Outbox event publish failed (retry {}/{}): eventId={}, error={}",
                    event.getRetryCount(), MAX_RETRIES, event.getEventId(), e.getMessage());
                break;
            }
        }
    }
}
