package com.ecommerce.product.infra.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.outbox.OutboxEvent;
import com.ecommerce.common.outbox.OutboxEventRepository;
import com.ecommerce.product.domain.event.StockReservationConfirmedEvent;
import com.ecommerce.product.domain.event.StockReservationReleasedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductOutboxEventHandlerTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private ProductOutboxEventHandler handler;

    @Test
    void handleStockReservationConfirmed_savesOutboxEvent() {
        StockReservationConfirmedEvent event = new StockReservationConfirmedEvent(1L, "ORDER-001");
        given(outboxRepository.save(any(OutboxEvent.class))).willAnswer(inv -> inv.getArgument(0));

        handler.handleStockReservationConfirmed(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("ProductStockReservation");
        assertThat(saved.getAggregateId()).isEqualTo("1");
        assertThat(saved.getEventType()).isEqualTo(KafkaTopics.STOCK_RESERVATION_CONFIRMED);
        assertThat(saved.getPartitionKey()).isEqualTo("ORDER-001");
        assertThat(saved.getPayload()).contains("ORDER-001");
    }

    @Test
    void handleStockReservationReleased_savesOutboxEvent() {
        StockReservationReleasedEvent event = new StockReservationReleasedEvent(1L, "ORDER-001");
        given(outboxRepository.save(any(OutboxEvent.class))).willAnswer(inv -> inv.getArgument(0));

        handler.handleStockReservationReleased(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("ProductStockReservation");
        assertThat(saved.getEventType()).isEqualTo(KafkaTopics.STOCK_RESERVATION_RELEASED);
        assertThat(saved.getPartitionKey()).isEqualTo("ORDER-001");
    }
}
