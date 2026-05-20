package com.ecommerce.order.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "saga_instance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SagaInstance extends BaseEntity {

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaState state;

    @Column(length = 500)
    private String failureReason;

    public static SagaInstance create(Long orderId, String orderNumber) {
        SagaInstance instance = new SagaInstance();
        instance.orderId = orderId;
        instance.orderNumber = orderNumber;
        instance.state = SagaState.STOCK_RESERVATION_PENDING;
        return instance;
    }

    public void moveToStockReserved() {
        validateState(SagaState.STOCK_RESERVATION_PENDING);
        this.state = SagaState.STOCK_RESERVED;
    }

    public void moveToStockReservationFailed(String reason) {
        validateState(SagaState.STOCK_RESERVATION_PENDING);
        this.state = SagaState.STOCK_RESERVATION_FAILED;
        this.failureReason = reason;
    }

    public void moveToPaymentProcessing() {
        validateState(SagaState.STOCK_RESERVED);
        this.state = SagaState.PAYMENT_PROCESSING;
    }

    public void moveToCompleted() {
        validateState(SagaState.PAYMENT_PROCESSING);
        this.state = SagaState.COMPLETED;
    }

    public void moveToCompensating() {
        validateState(SagaState.PAYMENT_PROCESSING);
        this.state = SagaState.COMPENSATING;
    }

    public void moveToCompensated() {
        validateState(SagaState.COMPENSATING);
        this.state = SagaState.COMPENSATED;
    }

    public void moveToCompensationRetryRequired(String reason) {
        validateState(SagaState.COMPENSATING);
        this.state = SagaState.COMPENSATION_RETRY_REQUIRED;
        this.failureReason = reason;
    }

    public void moveToFailed(String reason) {
        this.state = SagaState.FAILED;
        this.failureReason = reason;
    }

    private void validateState(SagaState expected) {
        if (this.state != expected) {
            throw new IllegalStateException(
                    "Expected SAGA state " + expected + " but was " + this.state);
        }
    }
}
