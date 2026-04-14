package com.ecommerce.payment.infra.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.outbox.OutboxEvent;
import com.ecommerce.common.outbox.OutboxEventRepository;
import com.ecommerce.payment.domain.event.PaymentCompletedEvent;
import com.ecommerce.payment.domain.event.PaymentFailedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxEventHandlerTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private PaymentOutboxEventHandler handler;

    @Test
    void handlePaymentCompleted_savesOutboxEvent() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
            "ORDER-001", 1L, 10L, "TXN-123", new BigDecimal("100.00"));
        given(outboxRepository.save(any(OutboxEvent.class))).willAnswer(inv -> inv.getArgument(0));

        handler.handlePaymentCompleted(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("Payment");
        assertThat(saved.getAggregateId()).isEqualTo("1");
        assertThat(saved.getEventType()).isEqualTo(KafkaTopics.PAYMENT_COMPLETED);
        assertThat(saved.getPartitionKey()).isEqualTo("ORDER-001");
        assertThat(saved.getPayload()).contains("TXN-123");
        assertThat(saved.isPublished()).isFalse();
    }

    @Test
    void handlePaymentFailed_savesOutboxEvent() {
        PaymentFailedEvent event = new PaymentFailedEvent("ORDER-002", 2L, "insufficient funds");
        given(outboxRepository.save(any(OutboxEvent.class))).willAnswer(inv -> inv.getArgument(0));

        handler.handlePaymentFailed(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("Payment");
        assertThat(saved.getAggregateId()).isEqualTo("2");
        assertThat(saved.getEventType()).isEqualTo(KafkaTopics.PAYMENT_FAILED);
        assertThat(saved.getPartitionKey()).isEqualTo("ORDER-002");
        assertThat(saved.getPayload()).contains("insufficient funds");
    }
}
