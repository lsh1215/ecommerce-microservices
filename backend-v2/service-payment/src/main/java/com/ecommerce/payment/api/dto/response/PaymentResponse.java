package com.ecommerce.payment.api.dto.response;

import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentMethod;
import com.ecommerce.payment.domain.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long orderId,
        String orderNumber,
        BigDecimal amount,
        PaymentStatus status,
        PaymentMethod paymentMethod,
        String transactionId,
        String failureReason,
        LocalDateTime paidAt,
        LocalDateTime refundedAt,
        LocalDateTime createdAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getOrderNumber(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPaymentMethod(),
                payment.getTransactionId(),
                payment.getFailureReason(),
                payment.getPaidAt(),
                payment.getRefundedAt(),
                payment.getCreatedAt()
        );
    }
}
