package com.ecommerce.order.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import com.ecommerce.order.application.dto.CreateOrderCommand;
import com.ecommerce.order.application.dto.OrderItemCommand;
import com.ecommerce.order.application.dto.ProductSnapshotDto;
import com.ecommerce.order.application.dto.ShippingAddressCommand;
import com.ecommerce.order.application.dto.StockReservation;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.model.ShippingAddress;
import com.ecommerce.order.domain.model.VariantSnapshot;
import com.ecommerce.order.domain.service.ProductCatalogPort;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    @Mock
    OrderSagaTransactions transactions;

    @Mock
    ProductCatalogPort productCatalog;

    @InjectMocks
    OrderSagaOrchestrator orchestrator;

    @Test
    @DisplayName("주문 시작은 로컬 트랜잭션 생성 후 트랜잭션 밖에서 재고를 예약하고 결제 단계로 전이한다")
    void startSaga_reservesStockOutsideOrderTransaction() {
        given(transactions.createPendingOrder(any()))
                .willReturn(new PendingOrder(1L, "ORD-001"));
        given(productCatalog.reserveStockAndFetchSnapshot(anyLong(), anyLong(), anyInt()))
                .willAnswer(inv -> snapshotDto(inv.getArgument(1)));
        Order completedOrder = buildOrderWithItem(100L, 1);
        given(transactions.completeStockReservation(eq(1L), any()))
                .willReturn(completedOrder);

        Order result = orchestrator.startSaga(validCommand());

        assertThat(result).isSameAs(completedOrder);
        InOrder inOrder = inOrder(transactions, productCatalog);
        inOrder.verify(transactions).createPendingOrder(any());
        inOrder.verify(productCatalog).reserveStockAndFetchSnapshot(1L, 100L, 1);
        inOrder.verify(transactions).completeStockReservation(eq(1L), any());
    }

    @Test
    @DisplayName("재고 예약 HTTP 호출 시 Order 트랜잭션이 열려 있지 않다")
    void startSaga_productCall_hasNoActiveOrderTransaction() {
        given(transactions.createPendingOrder(any()))
                .willReturn(new PendingOrder(1L, "ORD-001"));
        given(productCatalog.reserveStockAndFetchSnapshot(anyLong(), anyLong(), anyInt()))
                .willReturn(snapshotDto(100L));
        given(transactions.completeStockReservation(eq(1L), any()))
                .willReturn(buildOrderWithItem(100L, 1));

        orchestrator.startSaga(validCommand());

        verify(productCatalog).reserveStockAndFetchSnapshot(eq(1L), eq(100L), eq(1));
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    @DisplayName("두 번째 아이템 예약 실패 시 이미 예약된 아이템을 해제하고 실패 상태를 기록한다")
    void startSaga_partialReservationFailure_releasesReservedItemsAndRecordsFailure() {
        given(transactions.createPendingOrder(any()))
                .willReturn(new PendingOrder(1L, "ORD-001"));
        given(productCatalog.reserveStockAndFetchSnapshot(eq(1L), eq(100L), anyInt()))
                .willReturn(snapshotDto(100L));
        BusinessException failure = new BusinessException(OrderErrorCode.STOCK_RESERVATION_FAILED);
        given(productCatalog.reserveStockAndFetchSnapshot(eq(1L), eq(200L), anyInt()))
                .willThrow(failure);

        CreateOrderCommand twoItemCommand = new CreateOrderCommand(
                1L,
                List.of(itemCommand(100L, 1), itemCommand(200L, 1)),
                defaultAddress(),
                null
        );

        assertThatThrownBy(() -> orchestrator.startSaga(twoItemCommand))
                .isSameAs(failure);
        verify(productCatalog).releaseStock(1L, 100L, 1);
        verify(productCatalog, never()).releaseStock(eq(1L), eq(200L), anyInt());
        verify(transactions).markStockReservationFailed(
                eq(1L),
                eq(List.of(new StockReservation(1L, 100L, 1))),
                eq(failure)
        );
        verify(transactions, never()).completeStockReservation(anyLong(), any());
    }

    @Test
    @DisplayName("결제 완료 이벤트는 재고 확정 요청 트랜잭션으로 위임한다")
    void handlePaymentCompleted_delegatesToTransactionBoundary() {
        orchestrator.handlePaymentCompleted("ORD-001", 1L, 10L, "TX-001", new BigDecimal("100.00"));

        verify(transactions).requestStockConfirmation("ORD-001", 1L, 10L, "TX-001", new BigDecimal("100.00"));
        verify(productCatalog, never()).confirmReservation(anyLong(), anyLong());
    }

    @Test
    @DisplayName("결제 실패 이벤트는 재고 해제 요청 트랜잭션으로 위임한다")
    void handlePaymentFailed_requestsStockRelease() {
        orchestrator.handlePaymentFailed("ORD-001", 1L, "stub rejection");

        verify(transactions).requestStockRelease("ORD-001", 1L, "stub rejection");
        verify(productCatalog, never()).releaseStock(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("재고 확정 완료 이벤트는 결제 완료 마무리 트랜잭션으로 위임한다")
    void handleStockReservationConfirmed_delegatesToCompletion() {
        orchestrator.handleStockReservationConfirmed("ORD-001");

        verify(transactions).completePaymentAfterStockConfirmed("ORD-001");
    }

    @Test
    @DisplayName("재고 해제 완료 이벤트는 보상 완료 트랜잭션으로 위임한다")
    void handleStockReservationReleased_delegatesToCompensationCompletion() {
        orchestrator.handleStockReservationReleased("ORD-001");

        verify(transactions).completeCompensationAfterStockReleased("ORD-001");
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

    private Order buildOrderWithItem(Long variantId, int qty) {
        ShippingAddress address = new ShippingAddress("John", "010-0000-0000", "12345", "St 1", null);
        Order order = Order.create(1L, "ORD-001", address, null);
        VariantSnapshot snapshot = new VariantSnapshot(variantId, variantId, "Test Product",
                "M", "White", BigDecimal.valueOf(50000));
        order.addItem(com.ecommerce.order.domain.model.OrderItem.create(snapshot, qty));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        return order;
    }
}
