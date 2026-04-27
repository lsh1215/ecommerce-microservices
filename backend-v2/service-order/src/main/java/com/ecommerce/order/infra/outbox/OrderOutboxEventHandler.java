package com.ecommerce.order.infra.outbox;

import com.ecommerce.order.domain.event.OrderCancelledEvent;
import com.ecommerce.order.domain.event.OrderCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * No-outbox variant: publish to Kafka directly AFTER_COMMIT, skipping the
 * transactional outbox row write.
 *
 * <p>Failure mode this exposes:
 * <ul>
 *   <li>DB commits the Order row, then the broker is unreachable → the event
 *       is lost; downstream services never see it. Phase 2's outbox would
 *       have persisted the event in the same transaction and republished
 *       on the next polling tick.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderOutboxEventHandler {

    private final KafkaTemplate<String, String> stringKafkaTemplate;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            stringKafkaTemplate.send(event.getEventType(), event.getOrderNumber(),
                    objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            // No outbox to retry from — event is lost.
            log.error("[no-outbox] OrderCreated send failed; event lost: orderNumber={}",
                    event.getOrderNumber(), e);
        }
    }

    @SneakyThrows
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCancelled(OrderCancelledEvent event) {
        try {
            stringKafkaTemplate.send(event.getEventType(), event.getOrderNumber(),
                    objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("[no-outbox] OrderCancelled send failed; event lost: orderNumber={}",
                    event.getOrderNumber(), e);
        }
    }
}
