package com.ecommerce.payment.infra.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.idempotency.IdempotentEventHandler;
import com.ecommerce.payment.application.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    PaymentService paymentService;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    IdempotentEventHandler idempotentEventHandler;

    @InjectMocks
    OrderEventConsumer consumer;

    // --- JSON helpers ---

    private static String orderCreatedJson(String eventId, long orderId, String orderNumber, String totalAmount) {
        return String.format(
                "{\"eventId\":\"%s\",\"orderId\":%d,\"orderNumber\":\"%s\",\"totalAmount\":\"%s\"}",
                eventId, orderId, orderNumber, totalAmount);
    }

    private static String orderCancelledJson(String eventId, long orderId, String orderNumber) {
        return String.format(
                "{\"eventId\":\"%s\",\"orderId\":%d,\"orderNumber\":\"%s\"}",
                eventId, orderId, orderNumber);
    }

    // --- Tests ---

    @Test
    @DisplayName("유효한 order.created 메시지 수신 시 PaymentService.processFromEvent를 호출한다")
    void handleOrderCreated_validMessage_delegatesToPaymentService() {
        // Given
        given(idempotentEventHandler.tryProcess(eq("evt-1"), eq(KafkaTopics.ORDER_CREATED), any(Runnable.class)))
                .willAnswer(invocation -> {
                    Runnable processor = invocation.getArgument(2);
                    processor.run();
                    return true;
                });

        // When
        consumer.handleOrderCreated(orderCreatedJson("evt-1", 1L, "ORD-001", "100.00"));

        // Then
        verify(idempotentEventHandler).tryProcess(eq("evt-1"), eq(KafkaTopics.ORDER_CREATED), any());
        verify(paymentService).processFromEvent(
                eq(1L),
                eq("ORD-001"),
                argThat(a -> a.compareTo(new BigDecimal("100.00")) == 0));
    }

    @Test
    @DisplayName("유효한 order.cancelled 메시지 수신 시 PaymentService.cancelFromEvent를 호출한다")
    void handleOrderCancelled_validMessage_delegatesToPaymentService() {
        // Given
        given(idempotentEventHandler.tryProcess(eq("evt-2"), eq(KafkaTopics.ORDER_CANCELLED), any(Runnable.class)))
                .willAnswer(invocation -> {
                    Runnable processor = invocation.getArgument(2);
                    processor.run();
                    return true;
                });

        // When
        consumer.handleOrderCancelled(orderCancelledJson("evt-2", 1L, "ORD-001"));

        // Then
        verify(idempotentEventHandler).tryProcess(eq("evt-2"), eq(KafkaTopics.ORDER_CANCELLED), any());
        verify(paymentService).cancelFromEvent(eq(1L));
    }

    @Test
    @DisplayName("중복 order.created 이벤트는 PaymentService를 호출하지 않는다")
    void handleOrderCreated_duplicateEvent_skipped() {
        // Given — idempotentEventHandler가 false를 반환하며 Runnable을 실행하지 않음
        given(idempotentEventHandler.tryProcess(anyString(), anyString(), any(Runnable.class)))
                .willReturn(false);

        // When
        consumer.handleOrderCreated(orderCreatedJson("evt-1", 1L, "ORD-001", "100.00"));

        // Then
        verify(paymentService, never()).processFromEvent(any(), anyString(), any());
    }

    @Test
    @DisplayName("JSON 파싱 실패 시 RuntimeException을 던진다")
    void handleOrderCreated_malformedJson_throwsRuntimeException() {
        // When / Then
        assertThatThrownBy(() -> consumer.handleOrderCreated("not-json"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process order.created event");
    }
}
