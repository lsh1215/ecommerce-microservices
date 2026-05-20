package com.ecommerce.payment.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.payment.PaymentErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@Entity
@Table(
        name = "payment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_order_id",
                columnNames = "order_id"
        )
)
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private String orderNumber;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Column
    private String transactionId;

    @Column
    private String failureReason;

    @Column
    private LocalDateTime paidAt;

    @Column
    private LocalDateTime refundedAt;

    protected Payment() {}

    public static Payment create(Long orderId, String orderNumber, BigDecimal amount, PaymentMethod paymentMethod) {
        if (orderId == null) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_DATA,
                    "orderId must not be null");
        }
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_DATA,
                    "orderNumber must not be blank");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_DATA,
                    "amount must be greater than zero");
        }
        if (paymentMethod == null) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_DATA,
                    "paymentMethod must not be null");
        }
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.orderNumber = orderNumber;
        payment.amount = amount;
        payment.paymentMethod = paymentMethod;
        payment.status = PaymentStatus.PENDING;
        return payment;
    }

    public void markCompleted(String transactionId) {
        if (!status.canTransitionTo(PaymentStatus.COMPLETED)) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS,
                    "Cannot transition from " + status + " to COMPLETED");
        }
        this.status = PaymentStatus.COMPLETED;
        this.transactionId = transactionId;
        this.paidAt = LocalDateTime.now();
    }

    public void markFailed(String reason) {
        if (!status.canTransitionTo(PaymentStatus.FAILED)) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS,
                    "Cannot transition from " + status + " to FAILED");
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public void markRefunded() {
        if (!status.canTransitionTo(PaymentStatus.REFUNDED)) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS,
                    "Cannot transition from " + status + " to REFUNDED");
        }
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = LocalDateTime.now();
    }
}
