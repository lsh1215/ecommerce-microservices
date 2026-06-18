package com.ecommerce.order.infra.outbox;

import com.ecommerce.common.outbox.OutboxEvent;
import com.ecommerce.common.outbox.OutboxEventRepository;
import com.ecommerce.order.domain.event.OrderCancelledEvent;
import com.ecommerce.order.domain.event.OrderCreatedEvent;
import com.ecommerce.order.domain.event.PaymentRequestedEvent;
import com.ecommerce.order.domain.event.StockReservationConfirmRequestedEvent;
import com.ecommerce.order.domain.event.StockReservationReleaseRequestedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderOutboxEventHandler {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        outboxRepository.save(OutboxEvent.create(
            "Order",
            String.valueOf(event.getOrderId()),
            event.getEventType(),
            objectMapper.writeValueAsString(event),
            event.getOrderNumber()
        ));
    }

    @SneakyThrows
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handlePaymentRequested(PaymentRequestedEvent event) {
        outboxRepository.save(OutboxEvent.create(
            "Order",
            String.valueOf(event.getOrderId()),
            event.getEventType(),
            objectMapper.writeValueAsString(event),
            event.getOrderNumber()
        ));
    }

    @SneakyThrows
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleOrderCancelled(OrderCancelledEvent event) {
        outboxRepository.save(OutboxEvent.create(
            "Order",
            String.valueOf(event.getOrderId()),
            event.getEventType(),
            objectMapper.writeValueAsString(event),
            event.getOrderNumber()
        ));
    }

    @SneakyThrows
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleStockReservationConfirmRequested(StockReservationConfirmRequestedEvent event) {
        outboxRepository.save(OutboxEvent.create(
            "Order",
            String.valueOf(event.getOrderId()),
            event.getEventType(),
            objectMapper.writeValueAsString(event),
            event.getOrderNumber()
        ));
    }

    @SneakyThrows
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleStockReservationReleaseRequested(StockReservationReleaseRequestedEvent event) {
        outboxRepository.save(OutboxEvent.create(
            "Order",
            String.valueOf(event.getOrderId()),
            event.getEventType(),
            objectMapper.writeValueAsString(event),
            event.getOrderNumber()
        ));
    }
}
