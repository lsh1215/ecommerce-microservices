package com.ecommerce.order.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ecommerce.order.application.dto.ProductSnapshotDto;
import com.ecommerce.order.domain.event.OrderCreatedEvent;
import com.ecommerce.order.domain.event.PaymentRequestedEvent;
import com.ecommerce.order.domain.event.StockReservationConfirmRequestedEvent;
import com.ecommerce.order.domain.event.StockReservationReleaseRequestedEvent;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.model.SagaInstance;
import com.ecommerce.order.domain.model.SagaState;
import com.ecommerce.order.domain.model.ShippingAddress;
import com.ecommerce.order.domain.model.VariantSnapshot;
import com.ecommerce.order.domain.repository.OrderRepository;
import com.ecommerce.order.domain.repository.SagaInstanceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OrderSagaTransactionsTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    SagaInstanceRepository sagaRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("재고 예약 완료 시 주문 생성 이벤트와 결제 요청 이벤트를 함께 발행한다")
    void should_publish_order_created_and_payment_requested_when_stock_reservation_completes() {
        Order order = order("ORD-001");
        SagaInstance saga = SagaInstance.create(1L, "ORD-001");
        OrderSagaTransactions transactions =
                new OrderSagaTransactions(orderRepository, sagaRepository, eventPublisher);

        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(sagaRepository.findByOrderId(1L)).willReturn(Optional.of(saga));

        transactions.completeStockReservation(1L, List.of(new ReservedOrderItem(
                new ProductSnapshotDto(10L, 100L, "T-Shirt", "M", "White", BigDecimal.valueOf(10000)),
                2)));

        assertThat(saga.getState()).isEqualTo(SagaState.PAYMENT_PROCESSING);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .anySatisfy(event -> assertThat(event).isInstanceOf(OrderCreatedEvent.class))
                .anySatisfy(event -> assertThat(event).isInstanceOf(PaymentRequestedEvent.class));
    }

    @Test
    @DisplayName("결제 완료 수신 시 재고 확정 요청 이벤트를 발행하고 Saga를 결제 처리 상태로 유지한다")
    void requestStockConfirmation_publishesConfirmRequest() {
        Order order = orderWithItem("ORD-001", 100L, 2);
        SagaInstance saga = paymentProcessingSaga();
        OrderSagaTransactions transactions =
                new OrderSagaTransactions(orderRepository, sagaRepository, eventPublisher);

        given(sagaRepository.findByOrderNumber("ORD-001")).willReturn(Optional.of(saga));
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        transactions.requestStockConfirmation("ORD-001", 1L, 10L, "TX-001", BigDecimal.valueOf(10000));

        assertThat(saga.getState()).isEqualTo(SagaState.PAYMENT_PROCESSING);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isInstanceOfSatisfying(StockReservationConfirmRequestedEvent.class, event -> {
                    assertThat(event.getOrderNumber()).isEqualTo("ORD-001");
                    assertThat(event.getReservations()).hasSize(1);
                    assertThat(event.getReservations().get(0).variantId()).isEqualTo(100L);
                });
    }

    @Test
    @DisplayName("재고 확정 완료 수신 시 주문을 PAID로 바꾸고 Saga를 COMPLETED로 전이한다")
    void completePaymentAfterStockConfirmed_marksOrderPaid() {
        Order order = orderWithItem("ORD-001", 100L, 2);
        SagaInstance saga = stockConfirmingSaga();
        OrderSagaTransactions transactions =
                new OrderSagaTransactions(orderRepository, sagaRepository, eventPublisher);

        given(sagaRepository.findByOrderNumber("ORD-001")).willReturn(Optional.of(saga));
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        transactions.completePaymentAfterStockConfirmed("ORD-001");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(saga.getState()).isEqualTo(SagaState.COMPLETED);
    }

    @Test
    @DisplayName("결제 실패 수신 시 주문을 취소하고 재고 해제 요청 이벤트를 발행한다")
    void requestStockRelease_publishesReleaseRequest() {
        Order order = orderWithItem("ORD-001", 100L, 2);
        SagaInstance saga = paymentProcessingSaga();
        OrderSagaTransactions transactions =
                new OrderSagaTransactions(orderRepository, sagaRepository, eventPublisher);

        given(sagaRepository.findByOrderNumber("ORD-001")).willReturn(Optional.of(saga));
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        transactions.requestStockRelease("ORD-001", 1L, "payment failed");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSATING);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isInstanceOfSatisfying(StockReservationReleaseRequestedEvent.class, event -> {
                    assertThat(event.getReason()).isEqualTo("payment failed");
                    assertThat(event.getReservations().get(0).variantId()).isEqualTo(100L);
                });
    }

    @Test
    @DisplayName("재고 해제 완료 수신 시 Saga를 COMPENSATED로 전이한다")
    void completeCompensationAfterStockReleased_marksCompensated() {
        SagaInstance saga = compensatingSaga();
        OrderSagaTransactions transactions =
                new OrderSagaTransactions(orderRepository, sagaRepository, eventPublisher);

        given(sagaRepository.findByOrderNumber("ORD-001")).willReturn(Optional.of(saga));

        transactions.completeCompensationAfterStockReleased("ORD-001");

        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSATED);
    }

    private Order order(String orderNumber) {
        return Order.create(
                1L,
                orderNumber,
                new ShippingAddress("John", "010-0000-0000", "12345", "Seoul", null),
                null);
    }

    private Order orderWithItem(String orderNumber, Long variantId, int quantity) {
        Order order = order(orderNumber);
        order.addItem(OrderItem.create(
                new VariantSnapshot(10L, variantId, "T-Shirt", "M", "White", BigDecimal.valueOf(10000)),
                quantity));
        return order;
    }

    private SagaInstance paymentProcessingSaga() {
        SagaInstance saga = SagaInstance.create(1L, "ORD-001");
        saga.moveToStockReserved();
        saga.moveToPaymentProcessing();
        return saga;
    }

    private SagaInstance stockConfirmingSaga() {
        return paymentProcessingSaga();
    }

    private SagaInstance compensatingSaga() {
        SagaInstance saga = paymentProcessingSaga();
        saga.moveToCompensating();
        return saga;
    }
}
