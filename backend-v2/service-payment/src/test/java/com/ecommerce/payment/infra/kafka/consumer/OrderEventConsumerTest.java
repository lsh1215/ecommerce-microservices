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
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.CommonErrorCode;
import com.ecommerce.common.idempotency.IdempotentEventHandler;
import com.ecommerce.payment.application.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import org.assertj.core.api.Assertions;
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

    private static String paymentRequestedJson(String eventId, long orderId, String orderNumber, String totalAmount) {
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
    @DisplayName("유효한 payment.requested 메시지 수신 시 PaymentService.requestFromPaymentRequested를 호출한다")
    void handlePaymentRequested_validMessage_delegatesToPaymentService() {
        given(idempotentEventHandler.tryProcess(eq("evt-1"), eq(KafkaTopics.PAYMENT_REQUESTED), any(Runnable.class)))
                .willAnswer(invocation -> {
                    Runnable processor = invocation.getArgument(2);
                    processor.run();
                    return true;
                });

        consumer.handlePaymentRequested(paymentRequestedJson("evt-1", 1L, "ORD-001", "100.00"));

        verify(idempotentEventHandler).tryProcess(eq("evt-1"), eq(KafkaTopics.PAYMENT_REQUESTED), any());
        verify(paymentService).requestFromPaymentRequested(
                eq(1L),
                eq("ORD-001"),
                argThat(a -> a.compareTo(new BigDecimal("100.00")) == 0));
    }

    @Test
    @DisplayName("유효한 order.cancelled 메시지 수신 시 PaymentService.cancelFromEvent를 호출한다")
    void handleOrderCancelled_validMessage_delegatesToPaymentService() {
        given(idempotentEventHandler.tryProcess(eq("evt-2"), eq(KafkaTopics.ORDER_CANCELLED), any(Runnable.class)))
                .willAnswer(invocation -> {
                    Runnable processor = invocation.getArgument(2);
                    processor.run();
                    return true;
                });

        consumer.handleOrderCancelled(orderCancelledJson("evt-2", 1L, "ORD-001"));

        verify(idempotentEventHandler).tryProcess(eq("evt-2"), eq(KafkaTopics.ORDER_CANCELLED), any());
        verify(paymentService).cancelFromEvent(eq(1L));
    }

    @Test
    @DisplayName("중복 payment.requested 이벤트는 PaymentService를 호출하지 않는다")
    void handlePaymentRequested_duplicateEvent_skipped() {
        given(idempotentEventHandler.tryProcess(anyString(), anyString(), any(Runnable.class)))
                .willReturn(false);

        consumer.handlePaymentRequested(paymentRequestedJson("evt-1", 1L, "ORD-001", "100.00"));

        verify(paymentService, never()).requestFromPaymentRequested(any(), anyString(), any());
    }

    @Test
    @DisplayName("malformed JSON 은 BusinessException(INVALID_INPUT) 으로 변환되어 DefaultErrorHandler 가 DLT 로 라우팅한다")
    void handlePaymentRequested_malformedJson_throwsBusinessExceptionInvalidInput() {
        assertThatThrownBy(() -> consumer.handlePaymentRequested("not-json"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> Assertions.assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }
}
