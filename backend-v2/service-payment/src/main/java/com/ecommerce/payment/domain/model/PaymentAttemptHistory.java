package com.ecommerce.payment.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@Entity
@Table(name = "payment_attempt_history")
public class PaymentAttemptHistory extends BaseEntity {

    @Column
    private Long attemptId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentAttemptHistoryType type;

    @Column
    private String transactionId;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    protected PaymentAttemptHistory() {}

    public static PaymentAttemptHistory of(PaymentAttempt attempt, PaymentAttemptHistoryType type) {
        return of(attempt, type, null, null);
    }

    public static PaymentAttemptHistory of(PaymentAttempt attempt, PaymentAttemptHistoryType type,
                                           String transactionId, String reason) {
        PaymentAttemptHistory history = new PaymentAttemptHistory();
        history.attemptId = attempt.getId();
        history.orderId = attempt.getOrderId();
        history.orderNumber = attempt.getOrderNumber();
        history.type = type;
        history.transactionId = transactionId;
        history.reason = reason;
        history.occurredAt = LocalDateTime.now();
        return history;
    }
}
