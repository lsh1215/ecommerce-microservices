package com.ecommerce.product.infra.kafka.consumer;

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
import com.ecommerce.product.application.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockReservationCommandConsumerTest {

    @Mock
    ProductService productService;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    IdempotentEventHandler idempotentEventHandler;

    @InjectMocks
    StockReservationCommandConsumer consumer;

    @Test
    @DisplayName("stock.reservation.confirm.requested 메시지를 ProductService로 위임한다")
    void handleStockReservationConfirmRequested_delegatesViaIdempotencyHandler() {
        String json = stockCommandJson("evt-1", "ORD-001", 1L);
        given(idempotentEventHandler.tryProcess(eq("evt-1"),
                eq(KafkaTopics.STOCK_RESERVATION_CONFIRM_REQUESTED), any(Runnable.class)))
                .willAnswer(inv -> {
                    ((Runnable) inv.getArgument(2)).run();
                    return true;
                });

        consumer.handleStockReservationConfirmRequested(json);

        verify(productService).confirmReservationsAndPublish(1L, "ORD-001", List.of(100L, 200L));
    }

    @Test
    @DisplayName("stock.reservation.release.requested 메시지를 ProductService로 위임한다")
    void handleStockReservationReleaseRequested_delegatesViaIdempotencyHandler() {
        String json = stockCommandJson("evt-2", "ORD-001", 1L);
        given(idempotentEventHandler.tryProcess(eq("evt-2"),
                eq(KafkaTopics.STOCK_RESERVATION_RELEASE_REQUESTED), any(Runnable.class)))
                .willAnswer(inv -> {
                    ((Runnable) inv.getArgument(2)).run();
                    return true;
                });

        consumer.handleStockReservationReleaseRequested(json);

        verify(productService).releaseReservationsAndPublish(1L, "ORD-001", List.of(100L, 200L));
    }

    @Test
    @DisplayName("중복 command 이벤트는 ProductService를 호출하지 않는다")
    void duplicateCommand_skipsProductService() {
        String json = stockCommandJson("evt-1", "ORD-001", 1L);
        given(idempotentEventHandler.tryProcess(any(), any(), any())).willReturn(false);

        consumer.handleStockReservationConfirmRequested(json);

        verify(productService, never()).confirmReservationsAndPublish(any(), any(), any());
    }

    @Test
    @DisplayName("malformed JSON 은 BusinessException(INVALID_INPUT) 으로 변환한다")
    void malformedJson_throwsBusinessExceptionInvalidInput() {
        assertThatThrownBy(() -> consumer.handleStockReservationConfirmRequested("not-json"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> Assertions.assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    private static String stockCommandJson(String eventId, String orderNumber, Long orderId) {
        return String.format(
                "{\"eventId\":\"%s\",\"orderNumber\":\"%s\",\"orderId\":%d,"
                        + "\"reservations\":[{\"variantId\":100,\"quantity\":1},"
                        + "{\"variantId\":200,\"quantity\":2}]}",
                eventId, orderNumber, orderId);
    }
}
