package com.ecommerce.order.application.dto;

public record PaymentResult(
    Long paymentId,
    boolean success,
    String transactionId,
    String failureReason
) {}
