package com.ecommerce.payment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentStatusTest {

    @Test
    void pendingCanTransitionToCompleted() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.COMPLETED)).isTrue();
    }

    @Test
    void pendingCanTransitionToFailed() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FAILED)).isTrue();
    }

    @Test
    void completedCanTransitionToRefunded() {
        assertThat(PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.REFUNDED)).isTrue();
    }

    @Test
    void pendingCannotTransitionToRefunded() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.REFUNDED)).isFalse();
    }

    @Test
    void pendingCannotTransitionToPending() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.PENDING)).isFalse();
    }

    @Test
    void completedCannotTransitionToCompleted() {
        assertThat(PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.COMPLETED)).isFalse();
    }

    @Test
    void completedCannotTransitionToPending() {
        assertThat(PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.PENDING)).isFalse();
    }

    @Test
    void completedCannotTransitionToFailed() {
        assertThat(PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.FAILED)).isFalse();
    }

    @Test
    void failedCannotTransitionToAnything() {
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.PENDING)).isFalse();
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.COMPLETED)).isFalse();
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.REFUNDED)).isFalse();
    }

    @Test
    void refundedCannotTransitionToAnything() {
        assertThat(PaymentStatus.REFUNDED.canTransitionTo(PaymentStatus.PENDING)).isFalse();
        assertThat(PaymentStatus.REFUNDED.canTransitionTo(PaymentStatus.COMPLETED)).isFalse();
        assertThat(PaymentStatus.REFUNDED.canTransitionTo(PaymentStatus.FAILED)).isFalse();
        assertThat(PaymentStatus.REFUNDED.canTransitionTo(PaymentStatus.REFUNDED)).isFalse();
    }
}
