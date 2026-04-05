package com.ecommerce.payment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.common.exception.BusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentTest {

    @Test
    void create_withValidInputs_returnsPaymentInPendingStatus() {
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);

        assertThat(payment.getOrderId()).isEqualTo(1L);
        assertThat(payment.getOrderNumber()).isEqualTo("ORD-001");
        assertThat(payment.getAmount()).isEqualByComparingTo("100.00");
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getTransactionId()).isNull();
        assertThat(payment.getPaidAt()).isNull();
    }

    @Test
    void create_withNullOrderId_throwsIllegalArgument() {
        assertThatThrownBy(() -> Payment.create(null, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_withNonPositiveAmount_throwsIllegalArgument() {
        assertThatThrownBy(() -> Payment.create(1L, "ORD-001", BigDecimal.ZERO, PaymentMethod.CARD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_withNullMethod_throwsIllegalArgument() {
        assertThatThrownBy(() -> Payment.create(1L, "ORD-001", new BigDecimal("100.00"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markCompleted_fromPending_setsStatusAndPaidAt() {
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);

        payment.markCompleted("TX-001");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getTransactionId()).isEqualTo("TX-001");
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    void markCompleted_fromCompleted_throwsBusinessException() {
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        payment.markCompleted("TX-001");

        assertThatThrownBy(() -> payment.markCompleted("TX-002"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void markCompleted_fromFailed_throwsBusinessException() {
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        payment.markFailed("network error");

        assertThatThrownBy(() -> payment.markCompleted("TX-001"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void markFailed_fromPending_setsStatusAndReason() {
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);

        payment.markFailed("insufficient funds");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("insufficient funds");
    }

    @Test
    void markRefunded_fromCompleted_setsStatusAndRefundedAt() {
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        payment.markCompleted("TX-001");

        payment.markRefunded();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAt()).isNotNull();
    }

    @Test
    void markRefunded_fromPending_throwsBusinessException() {
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);

        assertThatThrownBy(() -> payment.markRefunded())
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void markRefunded_fromFailed_throwsBusinessException() {
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        payment.markFailed("network error");

        assertThatThrownBy(() -> payment.markRefunded())
                .isInstanceOf(BusinessException.class);
    }
}
