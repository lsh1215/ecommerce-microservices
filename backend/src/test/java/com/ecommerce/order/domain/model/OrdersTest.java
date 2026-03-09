package com.ecommerce.order.domain.model;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdersTest {

    @Test
    void create_shouldInitializeWithPendingStatus() {
        Orders order = Orders.create(1L, "idem-key-1", BigDecimal.ZERO, "USD", "123 Main St");

        assertThat(order.getCustomerId()).isEqualTo(1L);
        assertThat(order.getStatus()).isEqualTo("PENDING");
        assertThat(order.getIdempotencyKey()).isEqualTo("idem-key-1");
        assertThat(order.getTotalCurrency()).isEqualTo("USD");
        assertThat(order.getShippingAddress()).isEqualTo("123 Main St");
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, CONFIRMED",
            "PENDING, CANCELLED",
            "CONFIRMED, COMPLETED",
            "CONFIRMED, CANCELLED"
    })
    void transitionTo_shouldAllowValidTransitions(String from, String to) {
        Orders order = Orders.create(1L, "key", BigDecimal.ZERO, "USD", "addr");
        if (!from.equals("PENDING")) {
            order.transitionTo(from);
        }

        order.transitionTo(to);

        assertThat(order.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, COMPLETED",
            "CANCELLED, PENDING",
            "CANCELLED, CONFIRMED",
            "COMPLETED, CANCELLED",
            "COMPLETED, PENDING"
    })
    void transitionTo_shouldRejectInvalidTransitions(String from, String to) {
        Orders order = Orders.create(1L, "key", BigDecimal.ZERO, "USD", "addr");
        if (from.equals("CONFIRMED")) {
            order.transitionTo("CONFIRMED");
        } else if (from.equals("CANCELLED")) {
            order.transitionTo("CANCELLED");
        } else if (from.equals("COMPLETED")) {
            order.transitionTo("CONFIRMED");
            order.transitionTo("COMPLETED");
        }

        assertThatThrownBy(() -> order.transitionTo(to))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_CANCELLABLE));
    }

    @Test
    void addItem_shouldSetBidirectionalRelation() {
        Orders order = Orders.create(1L, "key", BigDecimal.ZERO, "USD", "addr");
        OrderItem item = OrderItem.create(10L, 2, "Product A", "Brand A",
                new BigDecimal("100.00"), "USD", "M", "SKU-001");

        order.addItem(item);

        assertThat(order.getItems()).hasSize(1);
        assertThat(item.getOrder()).isSameAs(order);
    }

    @Test
    void recalculateTotal_shouldSumAllItemSubtotals() {
        Orders order = Orders.create(1L, "key", BigDecimal.ZERO, "USD", "addr");
        order.addItem(OrderItem.create(1L, 2, "P1", "B1",
                new BigDecimal("50.00"), "USD", "M", "SKU-1"));
        order.addItem(OrderItem.create(2L, 3, "P2", "B2",
                new BigDecimal("30.00"), "USD", "L", "SKU-2"));

        order.recalculateTotal();

        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("190.00"));
    }

    @Test
    void orderItem_subtotal_shouldMultiplyPriceByQuantity() {
        OrderItem item = OrderItem.create(1L, 3, "Product", "Brand",
                new BigDecimal("29.99"), "USD", "L", "SKU-X");

        assertThat(item.subtotal()).isEqualByComparingTo(new BigDecimal("89.97"));
    }

    @Test
    void addStatusHistory_shouldSetBidirectionalRelation() {
        Orders order = Orders.create(1L, "key", BigDecimal.ZERO, "USD", "addr");
        OrderStatusHistory history = OrderStatusHistory.create(null, "PENDING", null);

        order.addStatusHistory(history);

        assertThat(order.getStatusHistories()).hasSize(1);
        assertThat(history.getOrder()).isSameAs(order);
    }
}
