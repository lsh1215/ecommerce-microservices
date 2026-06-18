package com.ecommerce.product.infra.outbox;

import com.ecommerce.common.outbox.OutboxEvent;
import com.ecommerce.common.outbox.OutboxEventRepository;
import com.ecommerce.product.domain.event.StockReservationConfirmedEvent;
import com.ecommerce.product.domain.event.StockReservationReleasedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ProductOutboxEventHandler {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleStockReservationConfirmed(StockReservationConfirmedEvent event) {
        outboxRepository.save(OutboxEvent.create(
                "ProductStockReservation",
                String.valueOf(event.getOrderId()),
                event.getEventType(),
                objectMapper.writeValueAsString(event),
                event.getOrderNumber()
        ));
    }

    @SneakyThrows
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleStockReservationReleased(StockReservationReleasedEvent event) {
        outboxRepository.save(OutboxEvent.create(
                "ProductStockReservation",
                String.valueOf(event.getOrderId()),
                event.getEventType(),
                objectMapper.writeValueAsString(event),
                event.getOrderNumber()
        ));
    }
}
