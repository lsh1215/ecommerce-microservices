package com.ecommerce.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderTest {

    private ShippingAddress defaultAddress() {
        return new ShippingAddress("John Doe", "010-1234-5678", "12345", "123 Main St", null);
    }

    private VariantSnapshot snapshot(BigDecimal unitPrice) {
        return new VariantSnapshot(1L, 100L, "Test Product", "M", "Black", unitPrice);
    }

    @Test
    void create_setsFieldsCorrectly() {
        Order order = Order.create(1L, "ORDER-001", defaultAddress(), "handle with care");

        assertThat(order.getCustomerId()).isEqualTo(1L);
        assertThat(order.getOrderNumber()).isEqualTo("ORDER-001");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.getMemo()).isEqualTo("handle with care");
        assertThat(order.getItems()).isEmpty();
    }

    @Test
    void addItem_calculatesTotalAmount() {
        Order order = Order.create(1L, "ORDER-001", defaultAddress(), null);
        OrderItem item1 = OrderItem.create(snapshot(new BigDecimal("100.00")), 2);
        OrderItem item2 = OrderItem.create(snapshot(new BigDecimal("50.00")), 3);

        order.addItem(item1);
        order.addItem(item2);

        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("350.00"));
        assertThat(order.getItems()).hasSize(2);
    }

    @Test
    void markConfirmed_fromPending() {
        Order order = Order.create(1L, "ORDER-001", defaultAddress(), null);

        order.markConfirmed();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void markPaid_fromConfirmed() {
        Order order = Order.create(1L, "ORDER-001", defaultAddress(), null);
        order.markConfirmed();

        order.markPaid();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void markPaid_fromPending_throws() {
        Order order = Order.create(1L, "ORDER-001", defaultAddress(), null);

        assertThatThrownBy(order::markPaid)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION));
    }

    @Test
    void markShipping_fromPaid() {
        Order order = Order.create(1L, "ORDER-001", defaultAddress(), null);
        order.markConfirmed();
        order.markPaid();

        order.markShipping();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
    }

    @Test
    void markDelivered_fromShipping() {
        Order order = Order.create(1L, "ORDER-001", defaultAddress(), null);
        order.markConfirmed();
        order.markPaid();
        order.markShipping();

        order.markDelivered();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void cancel_fromPending() {
        Order order = Order.create(1L, "ORDER-001", defaultAddress(), null);

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_fromConfirmed() {
        Order order = Order.create(1L, "ORDER-001", defaultAddress(), null);
        order.markConfirmed();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_fromPaid_throws() {
        Order order = Order.create(1L, "ORDER-001", defaultAddress(), null);
        order.markConfirmed();
        order.markPaid();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION));
    }

    @Test
    void fullLifecycle_pendingToDelivered() {
        Order order = Order.create(1L, "ORDER-001", defaultAddress(), null);
        order.addItem(OrderItem.create(snapshot(new BigDecimal("200.00")), 1));

        order.markConfirmed();
        order.markPaid();
        order.markShipping();
        order.markDelivered();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
    }
}
