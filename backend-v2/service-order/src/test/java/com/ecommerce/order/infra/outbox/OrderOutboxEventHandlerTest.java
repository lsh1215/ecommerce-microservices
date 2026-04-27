package com.ecommerce.order.infra.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.order.domain.event.OrderCancelledEvent;
import com.ecommerce.order.domain.event.OrderCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

@ExtendWith(MockitoExtension.class)
class OrderOutboxEventHandlerTest {

    @Mock
    private KafkaTemplate<String, String> stringKafkaTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private OrderOutboxEventHandler handler;

    @Test
    void handleOrderCreated_sendsToKafkaDirectly() {
        OrderCreatedEvent event = new OrderCreatedEvent(1L, "ORDER-001", 10L, new BigDecimal("100.00"));
        given(stringKafkaTemplate.send(any(String.class), any(String.class), any(String.class)))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        handler.handleOrderCreated(event);

        verify(stringKafkaTemplate).send(eq(KafkaTopics.ORDER_CREATED), eq("ORDER-001"), any(String.class));
    }

    @Test
    void handleOrderCancelled_sendsToKafkaDirectly() {
        OrderCancelledEvent event = new OrderCancelledEvent(2L, "ORDER-002", "payment failed");
        given(stringKafkaTemplate.send(any(String.class), any(String.class), any(String.class)))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        handler.handleOrderCancelled(event);

        verify(stringKafkaTemplate).send(eq(KafkaTopics.ORDER_CANCELLED), eq("ORDER-002"), any(String.class));
    }
}
