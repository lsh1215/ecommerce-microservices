package com.ecommerce.payment.api.dto.response;

import com.ecommerce.payment.domain.model.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        String publicId,
        Long orderId,
        BigDecimal amount,
        String currency,
        String status,
        String paymentMethod,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getPublicId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getPaymentMethod(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
