package com.ecommerce.payment.application.dto;

public record PaymentGatewayResult(
        boolean success,
        boolean retryable,
        String transactionId,
        String reason
) {

    public static PaymentGatewayResult success(String transactionId) {
        return new PaymentGatewayResult(true, false, transactionId, null);
    }

    public static PaymentGatewayResult failure(String reason) {
        return new PaymentGatewayResult(false, false, null, reason);
    }

    public static PaymentGatewayResult retryableFailure(String reason) {
        return new PaymentGatewayResult(false, true, null, reason);
    }
}
