package com.ecommerce.order.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ecommerce.order.application.dto.ProductSnapshotDto;
import com.ecommerce.order.domain.event.OrderCreatedEvent;
import com.ecommerce.order.domain.event.PaymentRequestedEvent;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.SagaInstance;
import com.ecommerce.order.domain.model.SagaState;
import com.ecommerce.order.domain.model.ShippingAddress;
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
        Order order = Order.create(
                1L,
                "ORD-001",
                new ShippingAddress("John", "010-0000-0000", "12345", "Seoul", null),
                null);
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
}
