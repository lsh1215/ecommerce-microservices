package com.ecommerce.order.infra.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.CommonErrorCode;
import com.ecommerce.common.idempotency.IdempotentEventHandler;
import com.ecommerce.order.application.saga.OrderSagaOrchestrator;
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
class PaymentEventConsumerTest {

    @Mock
    OrderSagaOrchestrator sagaOrchestrator;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    IdempotentEventHandler idempotentEventHandler;

    @InjectMocks
    PaymentEventConsumer consumer;

    @Test
    @DisplayName("유효한 payment.completed 메시지 수신 시 IdempotentEventHandler를 통해 orchestrator를 호출한다")
    void handlePaymentCompleted_validMessage_delegatesToOrchestratorViaIdempotencyHandler() {
        String json = paymentCompletedJson("evt-1", "ORD-001", 1L, 10L, "TX-001", "100.00");
        given(idempotentEventHandler.tryProcess(eq("evt-1"), eq(KafkaTopics.PAYMENT_COMPLETED), any(Runnable.class)))
                .willAnswer(inv -> {
                    ((Runnable) inv.getArgument(2)).run();
                    return true;
                });

        consumer.handlePaymentCompleted(json);

        verify(idempotentEventHandler).tryProcess(eq("evt-1"), eq(KafkaTopics.PAYMENT_COMPLETED), any());
        verify(sagaOrchestrator).handlePaymentCompleted(
                eq("ORD-001"), eq(1L), eq(10L), eq("TX-001"), any(BigDecimal.class));
    }

    @Test
    @DisplayName("유효한 payment.failed 메시지 수신 시 IdempotentEventHandler를 통해 orchestrator를 호출한다")
    void handlePaymentFailed_validMessage_delegatesToOrchestratorViaIdempotencyHandler() {
        String json = paymentFailedJson("evt-2", "ORD-001", 1L, "stub rejection");
        given(idempotentEventHandler.tryProcess(eq("evt-2"), eq(KafkaTopics.PAYMENT_FAILED), any(Runnable.class)))
                .willAnswer(inv -> {
                    ((Runnable) inv.getArgument(2)).run();
                    return true;
                });

        consumer.handlePaymentFailed(json);

        verify(sagaOrchestrator).handlePaymentFailed(eq("ORD-001"), eq(1L), eq("stub rejection"));
    }

    @Test
    @DisplayName("중복 payment.completed 이벤트는 orchestrator를 호출하지 않는다")
    void handlePaymentCompleted_duplicateEvent_orchestratorNotCalled() {
        String json = paymentCompletedJson("evt-1", "ORD-001", 1L, 10L, "TX-001", "100.00");
        given(idempotentEventHandler.tryProcess(any(), any(), any())).willReturn(false);

        consumer.handlePaymentCompleted(json);

        verify(sagaOrchestrator, never()).handlePaymentCompleted(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("malformed JSON 은 BusinessException(INVALID_INPUT) 으로 변환되어 DefaultErrorHandler 가 DLT 로 라우팅한다")
    void handlePaymentCompleted_malformedJson_throwsBusinessExceptionInvalidInput() {
        // Listener 가 JSON parse 실패를 BusinessException 으로 변환. KafkaConsumerConfig 의
        // DefaultErrorHandler.addNotRetryableExceptions 에 BusinessException 이 등록되어 있어
        // retry 없이 즉시 <topic>.DLT 로 라우팅됨 (poison pill 처리).
        assertThatThrownBy(() -> consumer.handlePaymentCompleted("not-json"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> Assertions.assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    // --- JSON helper methods ---

    private static String paymentCompletedJson(String eventId, String orderNumber,
                                               Long orderId, Long paymentId,
                                               String transactionId, String amount) {
        return String.format(
                "{\"eventId\":\"%s\",\"orderNumber\":\"%s\",\"orderId\":%d,"
                        + "\"paymentId\":%d,\"transactionId\":\"%s\",\"amount\":\"%s\"}",
                eventId, orderNumber, orderId, paymentId, transactionId, amount);
    }

    private static String paymentFailedJson(String eventId, String orderNumber,
                                            Long orderId, String reason) {
        return String.format(
                "{\"eventId\":\"%s\",\"orderNumber\":\"%s\",\"orderId\":%d,\"reason\":\"%s\"}",
                eventId, orderNumber, orderId, reason);
    }
}
