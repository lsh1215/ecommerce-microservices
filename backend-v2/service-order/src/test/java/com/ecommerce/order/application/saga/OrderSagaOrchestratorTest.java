package com.ecommerce.order.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import com.ecommerce.order.application.dto.CreateOrderCommand;
import com.ecommerce.order.application.dto.OrderItemCommand;
import com.ecommerce.order.application.dto.ProductSnapshotDto;
import com.ecommerce.order.application.dto.ShippingAddressCommand;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.model.SagaInstance;
import com.ecommerce.order.domain.model.SagaState;
import com.ecommerce.order.domain.repository.OrderRepository;
import com.ecommerce.order.domain.repository.SagaInstanceRepository;
import com.ecommerce.order.domain.service.ProductCatalogPort;
import com.ecommerce.order.infra.client.PaymentSyncClient;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    SagaInstanceRepository sagaRepository;

    @Mock
    ProductCatalogPort productCatalog;

    @Mock
    PaymentSyncClient paymentClient;

    @InjectMocks
    OrderSagaOrchestrator orchestrator;

    @Test
    @DisplayName("정상 주문 + Payment 동기 성공 시 Order는 PAID, SAGA는 COMPLETED")
    void startSaga_happyPath_paymentSyncSucceeds_returnsPaidOrder() {
        given(productCatalog.fetchSnapshot(anyLong())).willReturn(snapshotDto(100L));
        willDoNothing().given(productCatalog).reserveStock(anyLong(), anyInt());
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));
        given(sagaRepository.save(any(SagaInstance.class))).willAnswer(inv -> inv.getArgument(0));
        given(paymentClient.process(any(), any(), any()))
                .willReturn(new PaymentSyncClient.Result(true, 7L, "TX-9"));

        Order result = orchestrator.startSaga(validCommand());

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaRepository, times(1)).save(sagaCaptor.capture());
        assertThat(sagaCaptor.getValue().getState()).isEqualTo(SagaState.COMPLETED);
    }

    @Test
    @DisplayName("Payment 동기 거절 시 주문을 취소하고 재고를 해제한다")
    void startSaga_paymentRejected_cancelsOrder() {
        given(productCatalog.fetchSnapshot(anyLong())).willReturn(snapshotDto(100L));
        willDoNothing().given(productCatalog).reserveStock(anyLong(), anyInt());
        willDoNothing().given(productCatalog).releaseStock(anyLong(), anyInt());
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));
        given(sagaRepository.save(any(SagaInstance.class))).willAnswer(inv -> inv.getArgument(0));
        given(paymentClient.process(any(), any(), any()))
                .willReturn(new PaymentSyncClient.Result(false, 7L, null));

        Order result = orchestrator.startSaga(validCommand());

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(productCatalog).releaseStock(eq(100L), anyInt());
    }

    @Test
    @DisplayName("두 번째 아이템 재고 예약 실패 시 첫 번째 아이템의 재고를 해제하고 예외를 전파한다")
    void startSaga_stockInsufficient_releasesAlreadyReservedStockAndRethrows() {
        given(productCatalog.fetchSnapshot(anyLong())).willReturn(snapshotDto(100L));
        willDoNothing().given(productCatalog).reserveStock(eq(100L), anyInt());
        willThrow(new BusinessException(OrderErrorCode.STOCK_RESERVATION_FAILED))
                .given(productCatalog).reserveStock(eq(200L), anyInt());

        CreateOrderCommand twoItemCommand = new CreateOrderCommand(
                1L,
                List.of(itemCommand(100L, 1), itemCommand(200L, 1)),
                defaultAddress(),
                null
        );

        assertThatThrownBy(() -> orchestrator.startSaga(twoItemCommand))
                .isInstanceOf(BusinessException.class);
        verify(productCatalog).releaseStock(eq(100L), anyInt());
        verify(productCatalog, never()).releaseStock(eq(200L), anyInt());
        verify(orderRepository, never()).save(any());
    }

    private CreateOrderCommand validCommand() {
        return new CreateOrderCommand(
                1L,
                List.of(itemCommand(100L, 1)),
                defaultAddress(),
                null
        );
    }

    private ShippingAddressCommand defaultAddress() {
        return new ShippingAddressCommand("John Doe", "010-1234-5678", "12345", "123 Main St", null);
    }

    private OrderItemCommand itemCommand(Long variantId, int qty) {
        return new OrderItemCommand(variantId, variantId, "Test Product", "M", "White",
                BigDecimal.valueOf(50000), qty);
    }

    private ProductSnapshotDto snapshotDto(Long variantId) {
        return new ProductSnapshotDto(variantId, variantId, "Test Product", "M", "White",
                BigDecimal.valueOf(50000));
    }
}
