package com.ecommerce.order.domain.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum OrderStatus {

    PENDING,
    CONFIRMED,
    PAID,
    SHIPPING,
    DELIVERED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            PENDING, EnumSet.of(CONFIRMED, CANCELLED),
            CONFIRMED, EnumSet.of(PAID, CANCELLED),
            PAID, EnumSet.of(SHIPPING),
            SHIPPING, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class)
    );

    public boolean canTransitionTo(OrderStatus next) {
        return TRANSITIONS.getOrDefault(this, EnumSet.noneOf(OrderStatus.class)).contains(next);
    }
}
