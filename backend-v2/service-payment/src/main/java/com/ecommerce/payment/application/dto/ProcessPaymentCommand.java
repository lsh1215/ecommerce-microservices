package com.ecommerce.payment.application.dto;

import com.ecommerce.payment.domain.model.PaymentMethod;
import java.math.BigDecimal;

public record ProcessPaymentCommand(
        Long orderId,
        String orderNumber,
        BigDecimal amount,
        PaymentMethod paymentMethod
) {}
