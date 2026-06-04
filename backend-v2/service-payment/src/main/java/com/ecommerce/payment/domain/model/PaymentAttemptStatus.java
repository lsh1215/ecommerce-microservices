package com.ecommerce.payment.domain.model;

public enum PaymentAttemptStatus {

    REQUESTED,
    PROCESSING,
    COMPLETED,
    FAILED,
    RETRYABLE_FAILED,
    CANCELLED
}
