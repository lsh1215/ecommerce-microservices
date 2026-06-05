package com.ecommerce.payment.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConfirmPaymentRequest(
        @NotNull Long orderId,
        @NotBlank String providerPaymentKey
) {}
