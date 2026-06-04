package com.ecommerce.payment.domain.model;

public enum PaymentAttemptHistoryType {

    REQUESTED,
    PROCESSING_STARTED,
    COMPLETED,
    FAILED,
    RETRYABLE_FAILED,
    CANCELLED
}
