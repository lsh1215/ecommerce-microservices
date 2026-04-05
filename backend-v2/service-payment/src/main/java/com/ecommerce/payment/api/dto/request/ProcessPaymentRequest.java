package com.ecommerce.payment.api.dto.request;

import com.ecommerce.payment.domain.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ProcessPaymentRequest(
        @NotNull Long orderId,
        @NotBlank String orderNumber,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull PaymentMethod paymentMethod
) {}
