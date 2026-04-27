package com.ecommerce.payment.infra.outbox;

import com.ecommerce.payment.domain.event.PaymentCompletedEvent;
import com.ecommerce.payment.domain.event.PaymentFailedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentOutboxEventHandler {

    private final KafkaTemplate<String, String> stringKafkaTemplate;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        try {
            stringKafkaTemplate.send(event.getEventType(), event.getOrderNumber(),
                    objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("[no-outbox] PaymentCompleted send failed; event lost: orderNumber={}",
                    event.getOrderNumber(), e);
        }
    }

    @SneakyThrows
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailed(PaymentFailedEvent event) {
        try {
            stringKafkaTemplate.send(event.getEventType(), event.getOrderNumber(),
                    objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("[no-outbox] PaymentFailed send failed; event lost: orderNumber={}",
                    event.getOrderNumber(), e);
        }
    }
}
