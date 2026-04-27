package com.ecommerce.payment.infra.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.payment.domain.event.PaymentCompletedEvent;
import com.ecommerce.payment.domain.event.PaymentFailedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxEventHandlerTest {

    @Mock
    private KafkaTemplate<String, String> stringKafkaTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private PaymentOutboxEventHandler handler;

    @Test
    void handlePaymentCompleted_sendsToKafkaDirectly() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
            "ORDER-001", 1L, 10L, "TXN-123", new BigDecimal("100.00"));
        given(stringKafkaTemplate.send(any(String.class), any(String.class), any(String.class)))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        handler.handlePaymentCompleted(event);

        verify(stringKafkaTemplate).send(eq(KafkaTopics.PAYMENT_COMPLETED), eq("ORDER-001"), any(String.class));
    }

    @Test
    void handlePaymentFailed_sendsToKafkaDirectly() {
        PaymentFailedEvent event = new PaymentFailedEvent("ORDER-002", 2L, "insufficient funds");
        given(stringKafkaTemplate.send(any(String.class), any(String.class), any(String.class)))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        handler.handlePaymentFailed(event);

        verify(stringKafkaTemplate).send(eq(KafkaTopics.PAYMENT_FAILED), eq("ORDER-002"), any(String.class));
    }
}
