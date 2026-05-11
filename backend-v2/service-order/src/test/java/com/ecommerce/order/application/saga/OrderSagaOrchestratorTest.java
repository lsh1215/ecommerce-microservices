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
import com.ecommerce.order.domain.event.OrderCreatedEvent;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.model.SagaInstance;
import com.ecommerce.order.domain.model.SagaState;
import com.ecommerce.order.domain.model.ShippingAddress;
import com.ecommerce.order.domain.model.VariantSnapshot;
import com.ecommerce.order.domain.model.VirtualAccountInstruction;
import com.ecommerce.order.domain.repository.OrderRepository;
import com.ecommerce.order.domain.repository.SagaInstanceRepository;
import com.ecommerce.order.domain.service.ProductCatalogPort;
import com.ecommerce.order.domain.service.VirtualAccountIssuer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    SagaInstanceRepository sagaRepository;

    @Mock
    ProductCatalogPort productCatalog;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    VirtualAccountIssuer virtualAccountIssuer;

    @InjectMocks
    OrderSagaOrchestrator orchestrator;

    @Test
    @DisplayName("정상 주문 생성 시 PENDING 상태의 Order를 반환하고 SAGA를 PAYMENT_PROCESSING으로 전이한다")
    void startSaga_happyPath_returnsOrderInPendingState() {
        // Given — customerId is trusted from the X-Customer-Id header populated
        // by Traefik forwardAuth, so the orchestrator no longer calls
        // CustomerDirectoryPort.ensureExists.
        given(productCatalog.fetchSnapshot(anyLong())).willReturn(snapshotDto(100L));
        willDoNothing().given(productCatalog).reserveStock(anyLong(), anyInt());
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));
        given(sagaRepository.save(any(SagaInstance.class))).willAnswer(inv -> inv.getArgument(0));
        given(virtualAccountIssuer.issue(any(), any(), any())).willReturn(stubInstruction());

        // When
        Order result = orchestrator.startSaga(validCommand());

        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getTotalAmount()).isGreaterThan(BigDecimal.ZERO);
        verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));

        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaRepository, times(1)).save(sagaCaptor.capture());
        assertThat(sagaCaptor.getValue().getState()).isEqualTo(SagaState.PAYMENT_PROCESSING);
    }

    @Test
    @DisplayName("두 번째 아이템 재고 예약 실패 시 첫 번째 아이템의 재고를 해제하고 예외를 전파한다")
    void startSaga_stockInsufficient_releasesAlreadyReservedStockAndRethrows() {
        // Given
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

        // When / Then
        assertThatThrownBy(() -> orchestrator.startSaga(twoItemCommand))
                .isInstanceOf(BusinessException.class);
        verify(productCatalog).releaseStock(eq(100L), anyInt());
        verify(productCatalog, never()).releaseStock(eq(200L), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("결제 완료 이벤트 수신 시 Order를 PAID로, SAGA를 COMPLETED로 전이한다")
    void handlePaymentCompleted_updatesOrderAndSagaToFinalState() {
        // Given
        SagaInstance saga = SagaInstance.create(1L, "ORD-001");
        saga.moveToPaymentProcessing();
        ShippingAddress address = new ShippingAddress("John", "010-0000-0000", "12345", "St 1", null);
        Order order = Order.create(1L, "ORD-001", address, null);

        given(sagaRepository.findByOrderNumber("ORD-001")).willReturn(Optional.of(saga));
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        // When
        orchestrator.handlePaymentCompleted("ORD-001", 1L, 10L, "TX-001", new BigDecimal("100.00"));

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(saga.getState()).isEqualTo(SagaState.COMPLETED);
    }

    @Test
    @DisplayName("결제 실패 이벤트 수신 시 주문을 취소하고 예약된 재고를 해제하며 SAGA를 COMPENSATED로 전이한다")
    void handlePaymentFailed_cancelsOrderAndReleasesStock() {
        // Given
        SagaInstance saga = SagaInstance.create(1L, "ORD-001");
        saga.moveToPaymentProcessing();
        Order order = buildOrderWithItem(100L, 2);

        given(sagaRepository.findByOrderNumber("ORD-001")).willReturn(Optional.of(saga));
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        willDoNothing().given(productCatalog).releaseStock(anyLong(), anyInt());

        // When
        orchestrator.handlePaymentFailed("ORD-001", 1L, "stub rejection");

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSATED);
        verify(productCatalog).releaseStock(100L, 2);
    }

    // --- helper methods ---

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

    private VirtualAccountInstruction stubInstruction() {
        return new VirtualAccountInstruction("KB", "12345678901234", "ECOMMERCE STORE",
                BigDecimal.valueOf(50000), LocalDateTime.now().plusDays(7));
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
        order.addItem(OrderItem.create(snapshot, qty));
        return order;
    }
}
