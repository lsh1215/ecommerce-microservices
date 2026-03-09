package com.ecommerce.payment.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ErrorCode;
import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false, length = 26)
    private String publicId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @PrePersist
    void generatePublicId() {
        if (this.publicId == null) {
            this.publicId = UlidCreator.getUlid().toString();
        }
    }

    public static Payment create(Long orderId, String idempotencyKey, BigDecimal amount,
                                 String currency, String paymentMethod) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.idempotencyKey = idempotencyKey;
        payment.amount = amount;
        payment.currency = currency;
        payment.status = "PENDING";
        payment.paymentMethod = paymentMethod;
        return payment;
    }

    public void complete() {
        if (!"PENDING".equals(this.status)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot complete payment in status: " + this.status);
        }
        this.status = "COMPLETED";
    }

    public void fail() {
        if (!"PENDING".equals(this.status)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot fail payment in status: " + this.status);
        }
        this.status = "FAILED";
    }

    public void refund() {
        if (!"COMPLETED".equals(this.status)) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_REFUNDABLE,
                    "Cannot refund payment in status: " + this.status);
        }
        this.status = "REFUNDED";
    }
}
