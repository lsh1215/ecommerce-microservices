package com.ecommerce.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING, CONFIRMED",
            "PENDING, CANCELLED",
            "CONFIRMED, PAID",
            "CONFIRMED, CANCELLED",
            "PAID, SHIPPING",
            "SHIPPING, DELIVERED"
    })
    void canTransitionTo_validTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, PAID",
            "PENDING, SHIPPING",
            "PENDING, DELIVERED",
            "CONFIRMED, SHIPPING",
            "CONFIRMED, DELIVERED",
            "PAID, CONFIRMED",
            "PAID, CANCELLED",
            "PAID, DELIVERED",
            "SHIPPING, PAID",
            "SHIPPING, CANCELLED",
            "DELIVERED, PENDING",
            "DELIVERED, CANCELLED",
            "CANCELLED, PENDING",
            "CANCELLED, CONFIRMED"
    })
    void canTransitionTo_invalidTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void canTransitionTo_selfTransition_notAllowed() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.canTransitionTo(status)).isFalse();
        }
    }
}
