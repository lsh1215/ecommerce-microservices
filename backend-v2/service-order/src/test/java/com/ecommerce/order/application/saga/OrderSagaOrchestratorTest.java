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
        given(productCatalog.fetchSnapshot(anyLong())).willAnswer(inv -> snapshotDto(inv.getArgument(0)));
        willDoNothing().given(productCatalog).reserveStock(anyLong(), anyInt());
        Order completedOrder = buildOrderWithItem(100L, 1);
        given(transactions.completeStockReservation(eq(1L), any()))
                .willReturn(completedOrder);

        Order result = orchestrator.startSaga(validCommand());

        assertThat(result).isSameAs(completedOrder);
        InOrder inOrder = inOrder(transactions, productCatalog);
        inOrder.verify(transactions).createPendingOrder(any());
        inOrder.verify(productCatalog).fetchSnapshot(100L);
        inOrder.verify(productCatalog).reserveStock(100L, 1);
        inOrder.verify(transactions).completeStockReservation(eq(1L), any());
    }

    @Test
    @DisplayName("재고 예약 HTTP 호출 시 Order 트랜잭션이 열려 있지 않다")
    void startSaga_productCall_hasNoActiveOrderTransaction() {
        given(transactions.createPendingOrder(any()))
                .willReturn(new PendingOrder(1L, "ORD-001"));
        given(productCatalog.fetchSnapshot(anyLong())).willReturn(snapshotDto(100L));
        willDoNothing().given(productCatalog).reserveStock(anyLong(), anyInt());
        given(transactions.completeStockReservation(eq(1L), any()))
                .willReturn(buildOrderWithItem(100L, 1));

        orchestrator.startSaga(validCommand());

        verify(productCatalog).reserveStock(eq(100L), eq(1));
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    @DisplayName("두 번째 아이템 예약 실패 시 이미 예약된 아이템을 해제하고 실패 상태를 기록한다")
    void startSaga_partialReservationFailure_releasesReservedItemsAndRecordsFailure() {
        given(transactions.createPendingOrder(any()))
                .willReturn(new PendingOrder(1L, "ORD-001"));
        given(productCatalog.fetchSnapshot(anyLong())).willAnswer(inv -> snapshotDto(inv.getArgument(0)));
        willDoNothing().given(productCatalog).reserveStock(eq(100L), anyInt());
        BusinessException failure = new BusinessException(OrderErrorCode.STOCK_RESERVATION_FAILED);
        willThrow(failure).given(productCatalog).reserveStock(eq(200L), anyInt());

        CreateOrderCommand twoItemCommand = new CreateOrderCommand(
                1L,
                List.of(itemCommand(100L, 1), itemCommand(200L, 1)),
                defaultAddress(),
                null
        );

        assertThatThrownBy(() -> orchestrator.startSaga(twoItemCommand))
                .isSameAs(failure);
        verify(productCatalog).releaseStock(100L, 1);
        verify(productCatalog, never()).releaseStock(eq(200L), anyInt());
        verify(transactions).markStockReservationFailed(
                eq(1L),
                eq(List.of(new StockReservation(100L, 1))),
                eq(failure)
        );
        verify(transactions, never()).completeStockReservation(anyLong(), any());
    }

    @Test
    @DisplayName("결제 완료 이벤트는 짧은 로컬 트랜잭션으로 위임한다")
    void handlePaymentCompleted_delegatesToTransactionBoundary() {
        orchestrator.handlePaymentCompleted("ORD-001", 1L, 10L, "TX-001", new BigDecimal("100.00"));

        verify(transactions).completePayment("ORD-001", 1L, 10L, "TX-001", new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("결제 실패 보상은 주문 취소 트랜잭션 후 트랜잭션 밖에서 재고를 해제하고 보상 완료를 기록한다")
    void handlePaymentFailed_releasesStockOutsideOrderTransaction() {
        given(transactions.startCompensation("ORD-001"))
                .willReturn(List.of(new StockReservation(100L, 2)));
        willDoNothing().given(productCatalog).releaseStock(anyLong(), anyInt());

        orchestrator.handlePaymentFailed("ORD-001", 1L, "stub rejection");

        InOrder inOrder = inOrder(transactions, productCatalog);
        inOrder.verify(transactions).startCompensation("ORD-001");
        inOrder.verify(productCatalog).releaseStock(100L, 2);
        inOrder.verify(transactions).markCompensated("ORD-001");
    }

    @Test
    @DisplayName("보상 재고 해제 실패 시 보상 재시도 필요 상태를 기록하고 예외를 전파한다")
    void handlePaymentFailed_releaseFailure_recordsRetryRequiredAndRethrows() {
        RuntimeException failure = new RuntimeException("product timeout");
        given(transactions.startCompensation("ORD-001"))
                .willReturn(List.of(new StockReservation(100L, 2)));
        willThrow(failure).given(productCatalog).releaseStock(100L, 2);

        assertThatThrownBy(() -> orchestrator.handlePaymentFailed("ORD-001", 1L, "stub rejection"))
                .isSameAs(failure);

        verify(transactions).markCompensationRetryRequired("ORD-001", failure);
        verify(transactions, never()).markCompensated("ORD-001");
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
