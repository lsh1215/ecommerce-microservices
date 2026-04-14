package com.ecommerce.order.domain.model;

public enum SagaState {

    ORDER_CREATED,
    PAYMENT_PROCESSING,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}
