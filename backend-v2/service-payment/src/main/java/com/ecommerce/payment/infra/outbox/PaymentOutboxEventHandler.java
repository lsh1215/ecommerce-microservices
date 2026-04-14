package com.ecommerce.payment.infra.outbox;

import com.ecommerce.common.outbox.OutboxEvent;
import com.ecommerce.common.outbox.OutboxEventRepository;
import com.ecommerce.payment.domain.event.PaymentCompletedEvent;
import com.ecommerce.payment.domain.event.PaymentFailedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaymentOutboxEventHandler {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        outboxRepository.save(OutboxEvent.create(
            "Payment",
            String.valueOf(event.getOrderId()),
            event.getEventType(),
            objectMapper.writeValueAsString(event),
            event.getOrderNumber()
        ));
    }

    @SneakyThrows
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handlePaymentFailed(PaymentFailedEvent event) {
        outboxRepository.save(OutboxEvent.create(
            "Payment",
            String.valueOf(event.getOrderId()),
            event.getEventType(),
            objectMapper.writeValueAsString(event),
            event.getOrderNumber()
        ));
    }
}
