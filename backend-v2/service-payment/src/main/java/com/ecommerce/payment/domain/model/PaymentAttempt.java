package com.ecommerce.payment.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.payment.PaymentErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@Entity
@Table(name = "payment_attempt")
public class PaymentAttempt extends BaseEntity {

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private String orderNumber;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentAttemptStatus status;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column
    private String transactionId;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    @Column
    private LocalDateTime processingStartedAt;

    @Column
    private LocalDateTime completedAt;

    @Column
    private LocalDateTime failedAt;

    @Column(nullable = false)
    private int retryCount;

    protected PaymentAttempt() {}

    public static PaymentAttempt request(Long orderId, String orderNumber, BigDecimal amount,
                                         PaymentMethod paymentMethod) {
        validate(orderId, orderNumber, amount, paymentMethod);
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.orderId = orderId;
        attempt.orderNumber = orderNumber;
        attempt.amount = amount;
        attempt.paymentMethod = paymentMethod;
        attempt.status = PaymentAttemptStatus.REQUESTED;
        attempt.idempotencyKey = orderNumber;
        attempt.requestedAt = LocalDateTime.now();
        attempt.retryCount = 0;
        return attempt;
    }

    public void markProcessing() {
        if (status != PaymentAttemptStatus.REQUESTED && status != PaymentAttemptStatus.RETRYABLE_FAILED) {
            throw invalidTransition(PaymentAttemptStatus.PROCESSING);
        }
        this.status = PaymentAttemptStatus.PROCESSING;
        this.processingStartedAt = LocalDateTime.now();
        this.retryCount += 1;
    }

    public void markCompleted(String transactionId) {
        if (status != PaymentAttemptStatus.PROCESSING) {
            throw invalidTransition(PaymentAttemptStatus.COMPLETED);
        }
        this.status = PaymentAttemptStatus.COMPLETED;
        this.transactionId = transactionId;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String reason) {
        if (status != PaymentAttemptStatus.PROCESSING) {
            throw invalidTransition(PaymentAttemptStatus.FAILED);
        }
        this.status = PaymentAttemptStatus.FAILED;
        this.failureReason = reason;
        this.failedAt = LocalDateTime.now();
    }

    public void markRetryableFailed(String reason) {
        if (status != PaymentAttemptStatus.PROCESSING) {
            throw invalidTransition(PaymentAttemptStatus.RETRYABLE_FAILED);
        }
        this.status = PaymentAttemptStatus.RETRYABLE_FAILED;
        this.failureReason = reason;
        this.failedAt = LocalDateTime.now();
    }

    public void markCancelled(String reason) {
        if (status == PaymentAttemptStatus.COMPLETED || status == PaymentAttemptStatus.CANCELLED) {
            return;
        }
        this.status = PaymentAttemptStatus.CANCELLED;
        this.failureReason = reason;
        this.failedAt = LocalDateTime.now();
    }

    private static void validate(Long orderId, String orderNumber, BigDecimal amount,
                                 PaymentMethod paymentMethod) {
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
    }

    private BusinessException invalidTransition(PaymentAttemptStatus next) {
        return new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS,
                "Cannot transition payment attempt from " + status + " to " + next);
    }
}
