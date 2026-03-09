package com.ecommerce.payment.domain.model;

import com.ecommerce.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    void create_shouldInitializeWithPendingStatus() {
        Payment payment = Payment.create(1L, "idem-001", BigDecimal.valueOf(29900), "KRW", "CARD");

        assertThat(payment.getOrderId()).isEqualTo(1L);
        assertThat(payment.getIdempotencyKey()).isEqualTo("idem-001");
        assertThat(payment.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(29900));
        assertThat(payment.getCurrency()).isEqualTo("KRW");
        assertThat(payment.getStatus()).isEqualTo("PENDING");
        assertThat(payment.getPaymentMethod()).isEqualTo("CARD");
    }

    @Test
    void complete_shouldTransitionFromPendingToCompleted() {
        Payment payment = Payment.create(1L, "idem-001", BigDecimal.valueOf(100), "USD", null);

        payment.complete();

        assertThat(payment.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void complete_shouldThrowWhenNotPending() {
        Payment payment = Payment.create(1L, "idem-001", BigDecimal.valueOf(100), "USD", null);
        payment.complete();

        assertThatThrownBy(payment::complete)
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void fail_shouldTransitionFromPendingToFailed() {
        Payment payment = Payment.create(1L, "idem-001", BigDecimal.valueOf(100), "USD", null);

        payment.fail();

        assertThat(payment.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void fail_shouldThrowWhenNotPending() {
        Payment payment = Payment.create(1L, "idem-001", BigDecimal.valueOf(100), "USD", null);
        payment.complete();

        assertThatThrownBy(payment::fail)
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void refund_shouldTransitionFromCompletedToRefunded() {
        Payment payment = Payment.create(1L, "idem-001", BigDecimal.valueOf(100), "USD", null);
        payment.complete();

        payment.refund();

        assertThat(payment.getStatus()).isEqualTo("REFUNDED");
    }

    @Test
    void refund_shouldThrowWhenPending() {
        Payment payment = Payment.create(1L, "idem-001", BigDecimal.valueOf(100), "USD", null);

        assertThatThrownBy(payment::refund)
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void refund_shouldThrowWhenFailed() {
        Payment payment = Payment.create(1L, "idem-001", BigDecimal.valueOf(100), "USD", null);
        payment.fail();

        assertThatThrownBy(payment::refund)
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void refund_shouldThrowWhenAlreadyRefunded() {
        Payment payment = Payment.create(1L, "idem-001", BigDecimal.valueOf(100), "USD", null);
        payment.complete();
        payment.refund();

        assertThatThrownBy(payment::refund)
                .isInstanceOf(BusinessException.class);
    }
}
