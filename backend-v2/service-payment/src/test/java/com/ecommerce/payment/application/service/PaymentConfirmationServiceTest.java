package com.ecommerce.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ecommerce.payment.application.dto.ConfirmPaymentCommand;
import com.ecommerce.payment.application.dto.PaymentGatewayCommand;
import com.ecommerce.payment.application.dto.PaymentGatewayResult;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentMethod;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmationServiceTest {

    @Mock
    PaymentService paymentService;

    @Mock
    PaymentGatewayPort gatewayPort;

    @Test
    @DisplayName("confirm 요청은 트랜잭션 밖에서 PG를 승인하고 완료를 기록한다")
    void should_authorize_gateway_outside_transaction_and_complete_attempt() {
        PaymentGatewayCommand gatewayCommand = new PaymentGatewayCommand(
                10L, 1L, "ORD-001", new BigDecimal("100.00"),
                PaymentMethod.CARD, "ORD-001", "pay-key");
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        AtomicBoolean transactionActiveDuringGateway = new AtomicBoolean(true);

        given(paymentService.claimAttemptForConfirmation(1L, "pay-key"))
                .willReturn(Optional.of(gatewayCommand));
        given(gatewayPort.authorize(gatewayCommand)).willAnswer(invocation -> {
            transactionActiveDuringGateway.set(TransactionSynchronizationManager.isActualTransactionActive());
            return PaymentGatewayResult.success("TXN-001");
        });
        given(paymentService.completeAttempt(10L, "TXN-001")).willReturn(payment);

        PaymentConfirmationService service = new PaymentConfirmationService(paymentService, gatewayPort);

        Payment result = service.confirm(new ConfirmPaymentCommand(1L, "pay-key"));

        assertThat(result).isSameAs(payment);
        assertThat(transactionActiveDuringGateway).isFalse();
        verify(paymentService).completeAttempt(10L, "TXN-001");
    }

    @Test
    @DisplayName("PG 거절은 결제 실패로 기록한다")
    void should_record_failure_when_gateway_rejects() {
        PaymentGatewayCommand gatewayCommand = new PaymentGatewayCommand(
                10L, 1L, "ORD-001", new BigDecimal("100.00"),
                PaymentMethod.CARD, "ORD-001", "pay-key");
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);

        given(paymentService.claimAttemptForConfirmation(1L, "pay-key"))
                .willReturn(Optional.of(gatewayCommand));
        given(gatewayPort.authorize(gatewayCommand)).willReturn(PaymentGatewayResult.failure("card rejected"));
        given(paymentService.failAttempt(10L, "card rejected", false)).willReturn(payment);

        PaymentConfirmationService service = new PaymentConfirmationService(paymentService, gatewayPort);

        Payment result = service.confirm(new ConfirmPaymentCommand(1L, "pay-key"));

        assertThat(result).isSameAs(payment);
        verify(paymentService).failAttempt(10L, "card rejected", false);
    }

    @Test
    @DisplayName("이미 완료된 결제 confirm 재호출은 PG를 다시 호출하지 않고 기존 결제를 반환한다")
    void should_return_completed_payment_when_confirm_is_duplicated() {
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        payment.markCompleted("TXN-001");

        given(paymentService.claimAttemptForConfirmation(1L, "pay-key"))
                .willReturn(Optional.empty());
        given(paymentService.getByOrderId(1L)).willReturn(payment);

        PaymentConfirmationService service = new PaymentConfirmationService(paymentService, gatewayPort);

        Payment result = service.confirm(new ConfirmPaymentCommand(1L, "pay-key"));

        assertThat(result).isSameAs(payment);
        verify(gatewayPort, never()).authorize(any());
    }
}
