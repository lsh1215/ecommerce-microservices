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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductStockEventConsumerTest {

    @Mock
    OrderSagaOrchestrator sagaOrchestrator;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    IdempotentEventHandler idempotentEventHandler;

    @InjectMocks
    ProductStockEventConsumer consumer;

    @Test
    @DisplayName("stock.reservation.confirmed 메시지를 멱등 처리기로 위임한다")
    void handleStockReservationConfirmed_delegatesViaIdempotencyHandler() {
        String json = stockEventJson("evt-1", "ORD-001", 1L);
        given(idempotentEventHandler.tryProcess(eq("evt-1"), eq(KafkaTopics.STOCK_RESERVATION_CONFIRMED),
                any(Runnable.class))).willAnswer(inv -> {
                    ((Runnable) inv.getArgument(2)).run();
                    return true;
                });

        consumer.handleStockReservationConfirmed(json);

        verify(sagaOrchestrator).handleStockReservationConfirmed("ORD-001");
    }

    @Test
    @DisplayName("stock.reservation.released 메시지를 멱등 처리기로 위임한다")
    void handleStockReservationReleased_delegatesViaIdempotencyHandler() {
        String json = stockEventJson("evt-2", "ORD-001", 1L);
        given(idempotentEventHandler.tryProcess(eq("evt-2"), eq(KafkaTopics.STOCK_RESERVATION_RELEASED),
                any(Runnable.class))).willAnswer(inv -> {
                    ((Runnable) inv.getArgument(2)).run();
                    return true;
                });

        consumer.handleStockReservationReleased(json);

        verify(sagaOrchestrator).handleStockReservationReleased("ORD-001");
    }

    @Test
    @DisplayName("중복 stock.reservation.confirmed 이벤트는 orchestrator를 호출하지 않는다")
    void handleStockReservationConfirmed_duplicate_skipsOrchestrator() {
        String json = stockEventJson("evt-1", "ORD-001", 1L);
        given(idempotentEventHandler.tryProcess(any(), any(), any())).willReturn(false);

        consumer.handleStockReservationConfirmed(json);

        verify(sagaOrchestrator, never()).handleStockReservationConfirmed(any());
    }

    @Test
    @DisplayName("malformed JSON 은 BusinessException(INVALID_INPUT) 으로 변환한다")
    void malformedJson_throwsBusinessExceptionInvalidInput() {
        assertThatThrownBy(() -> consumer.handleStockReservationConfirmed("not-json"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> Assertions.assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    private static String stockEventJson(String eventId, String orderNumber, Long orderId) {
        return String.format(
                "{\"eventId\":\"%s\",\"orderNumber\":\"%s\",\"orderId\":%d}",
                eventId, orderNumber, orderId);
    }
}
