package com.ecommerce.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ecommerce.payment.application.dto.PaymentGatewayCommand;
import com.ecommerce.payment.application.dto.PaymentGatewayResult;
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
class PaymentAttemptProcessorTest {

    @Mock
    PaymentService paymentService;

    @Mock
    PaymentGatewayPort gatewayPort;

    @Test
    @DisplayName("요청된 결제 시도를 PG 트랜잭션 밖에서 승인하고 완료 처리한다")
    void should_call_gateway_outside_transaction_and_complete_attempt() {
        PaymentGatewayCommand command = new PaymentGatewayCommand(
                10L, 1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD, "ORD-001");
        AtomicBoolean transactionActiveDuringGateway = new AtomicBoolean(true);

        given(paymentService.claimNextAttempt()).willReturn(Optional.of(command));
        given(gatewayPort.authorize(command)).willAnswer(inv -> {
            transactionActiveDuringGateway.set(TransactionSynchronizationManager.isActualTransactionActive());
            return PaymentGatewayResult.success("TXN-001");
        });

        PaymentAttemptProcessor processor = new PaymentAttemptProcessor(paymentService, gatewayPort);

        boolean processed = processor.processOne();

        assertThat(processed).isTrue();
        assertThat(transactionActiveDuringGateway).isFalse();
        verify(paymentService).completeAttempt(10L, "TXN-001");
    }

    @Test
    @DisplayName("PG 거절은 retry 불가능 실패로 기록한다")
    void should_record_permanent_failure_when_gateway_rejects() {
        PaymentGatewayCommand command = new PaymentGatewayCommand(
                10L, 1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD, "ORD-001");

        given(paymentService.claimNextAttempt()).willReturn(Optional.of(command));
        given(gatewayPort.authorize(command)).willReturn(PaymentGatewayResult.failure("card rejected"));

        PaymentAttemptProcessor processor = new PaymentAttemptProcessor(paymentService, gatewayPort);

        assertThat(processor.processOne()).isTrue();
        verify(paymentService).failAttempt(10L, "card rejected", false);
    }

    @Test
    @DisplayName("처리할 결제 시도가 없으면 PG를 호출하지 않는다")
    void should_not_call_gateway_when_no_attempt_exists() {
        given(paymentService.claimNextAttempt()).willReturn(Optional.empty());

        PaymentAttemptProcessor processor = new PaymentAttemptProcessor(paymentService, gatewayPort);

        assertThat(processor.processOne()).isFalse();
        verify(gatewayPort, never()).authorize(any());
    }

    @Test
    @DisplayName("스케줄러 1회 실행 시 최대 batch size까지 결제 시도를 처리한다")
    void should_process_attempts_until_batch_size_or_empty() {
        PaymentGatewayCommand first = new PaymentGatewayCommand(
                10L, 1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD, "ORD-001");
        PaymentGatewayCommand second = new PaymentGatewayCommand(
                11L, 2L, "ORD-002", new BigDecimal("200.00"), PaymentMethod.CARD, "ORD-002");

        given(paymentService.claimNextAttempt())
                .willReturn(Optional.of(first))
                .willReturn(Optional.of(second))
                .willReturn(Optional.empty());
        given(gatewayPort.authorize(any())).willReturn(PaymentGatewayResult.success("TXN"));

        PaymentAttemptProcessor processor = new PaymentAttemptProcessor(paymentService, gatewayPort, 5);

        processor.processScheduled();

        verify(paymentService, times(3)).claimNextAttempt();
        verify(gatewayPort, times(2)).authorize(any());
        verify(paymentService).completeAttempt(10L, "TXN");
        verify(paymentService).completeAttempt(11L, "TXN");
    }

    @Test
    @DisplayName("batch size 설정이 0 이하이면 최소 1건을 처리한다")
    void should_process_at_least_one_attempt_when_batch_size_is_not_positive() {
        PaymentGatewayCommand command = new PaymentGatewayCommand(
                10L, 1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD, "ORD-001");

        given(paymentService.claimNextAttempt()).willReturn(Optional.of(command));
        given(gatewayPort.authorize(command)).willReturn(PaymentGatewayResult.success("TXN-001"));

        PaymentAttemptProcessor processor = new PaymentAttemptProcessor(paymentService, gatewayPort, 0);

        processor.processScheduled();

        verify(gatewayPort).authorize(command);
        verify(paymentService).completeAttempt(10L, "TXN-001");
    }
}
