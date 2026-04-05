package com.ecommerce.payment.domain.model;

public enum PaymentStatus {

    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED;

    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case PENDING -> next == COMPLETED || next == FAILED;
            case COMPLETED -> next == REFUNDED;
            default -> false;
        };
    }
}
