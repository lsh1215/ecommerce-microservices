package com.ecommerce.order.infra.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.outbox.OutboxEvent;
import com.ecommerce.common.outbox.OutboxEventRepository;
import com.ecommerce.order.domain.event.OrderCancelledEvent;
import com.ecommerce.order.domain.event.OrderCreatedEvent;
import com.ecommerce.order.domain.event.PaymentRequestedEvent;
import com.ecommerce.order.domain.event.StockReservationConfirmRequestedEvent;
import com.ecommerce.order.domain.event.StockReservationReleaseRequestedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderOutboxEventHandlerTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private OrderOutboxEventHandler handler;

    @Test
    void handleOrderCreated_savesOutboxEvent() {
        OrderCreatedEvent event = new OrderCreatedEvent(1L, "ORDER-001", 10L, new BigDecimal("100.00"));
        given(outboxRepository.save(any(OutboxEvent.class))).willAnswer(inv -> inv.getArgument(0));

        handler.handleOrderCreated(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("Order");
        assertThat(saved.getAggregateId()).isEqualTo("1");
        assertThat(saved.getEventType()).isEqualTo(KafkaTopics.ORDER_CREATED);
        assertThat(saved.getPartitionKey()).isEqualTo("ORDER-001");
        assertThat(saved.getPayload()).contains("ORDER-001");
        assertThat(saved.isPublished()).isFalse();
    }

    @Test
    void handlePaymentRequested_savesOutboxEvent() {
        PaymentRequestedEvent event = new PaymentRequestedEvent(1L, "ORDER-001", 10L, new BigDecimal("100.00"));
        given(outboxRepository.save(any(OutboxEvent.class))).willAnswer(inv -> inv.getArgument(0));

        handler.handlePaymentRequested(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("Order");
        assertThat(saved.getAggregateId()).isEqualTo("1");
        assertThat(saved.getEventType()).isEqualTo(KafkaTopics.PAYMENT_REQUESTED);
        assertThat(saved.getPartitionKey()).isEqualTo("ORDER-001");
        assertThat(saved.getPayload()).contains("ORDER-001");
    }

    @Test
    void handleOrderCancelled_savesOutboxEvent() {
        OrderCancelledEvent event = new OrderCancelledEvent(2L, "ORDER-002", "payment failed");
        given(outboxRepository.save(any(OutboxEvent.class))).willAnswer(inv -> inv.getArgument(0));

        handler.handleOrderCancelled(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("Order");
        assertThat(saved.getAggregateId()).isEqualTo("2");
        assertThat(saved.getEventType()).isEqualTo(KafkaTopics.ORDER_CANCELLED);
        assertThat(saved.getPartitionKey()).isEqualTo("ORDER-002");
        assertThat(saved.getPayload()).contains("payment failed");
    }

    @Test
    void handleStockReservationConfirmRequested_savesOutboxEvent() {
        StockReservationConfirmRequestedEvent event = new StockReservationConfirmRequestedEvent(
                1L,
                "ORDER-001",
                List.of(new StockReservationConfirmRequestedEvent.ReservationLine(100L, 2)));
        given(outboxRepository.save(any(OutboxEvent.class))).willAnswer(inv -> inv.getArgument(0));

        handler.handleStockReservationConfirmRequested(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("Order");
        assertThat(saved.getEventType()).isEqualTo(KafkaTopics.STOCK_RESERVATION_CONFIRM_REQUESTED);
        assertThat(saved.getPartitionKey()).isEqualTo("ORDER-001");
        assertThat(saved.getPayload()).contains("\"variantId\":100");
    }

    @Test
    void handleStockReservationReleaseRequested_savesOutboxEvent() {
        StockReservationReleaseRequestedEvent event = new StockReservationReleaseRequestedEvent(
                1L,
                "ORDER-001",
                "payment failed",
                List.of(new StockReservationReleaseRequestedEvent.ReservationLine(100L, 2)));
        given(outboxRepository.save(any(OutboxEvent.class))).willAnswer(inv -> inv.getArgument(0));

        handler.handleStockReservationReleaseRequested(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("Order");
        assertThat(saved.getEventType()).isEqualTo(KafkaTopics.STOCK_RESERVATION_RELEASE_REQUESTED);
        assertThat(saved.getPartitionKey()).isEqualTo("ORDER-001");
        assertThat(saved.getPayload()).contains("payment failed");
    }
}
