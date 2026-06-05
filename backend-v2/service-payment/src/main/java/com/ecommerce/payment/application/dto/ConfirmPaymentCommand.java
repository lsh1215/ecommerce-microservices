package com.ecommerce.payment.application.dto;

public record ConfirmPaymentCommand(
        Long orderId,
        String providerPaymentKey
) {}
