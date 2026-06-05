package com.ecommerce.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ecommerce.payment.application.dto.ProcessPaymentCommand;
import com.ecommerce.payment.domain.event.PaymentCompletedEvent;
import com.ecommerce.payment.domain.event.PaymentFailedEvent;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentAttempt;
import com.ecommerce.payment.domain.model.PaymentAttemptHistory;
import com.ecommerce.payment.domain.model.PaymentAttemptHistoryType;
import com.ecommerce.payment.domain.model.PaymentAttemptStatus;
import com.ecommerce.payment.domain.model.PaymentMethod;
import com.ecommerce.payment.domain.model.PaymentStatus;
import com.ecommerce.payment.domain.repository.PaymentAttemptHistoryRepository;
import com.ecommerce.payment.domain.repository.PaymentAttemptRepository;
import com.ecommerce.payment.domain.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    PaymentAttemptRepository attemptRepository;

    @Mock
    PaymentAttemptHistoryRepository historyRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository,
                attemptRepository,
                historyRepository,
                eventPublisher);
    }

    @Test
    @DisplayName("payment.requested 수신 시 PG 호출 없이 결제 요청 행과 요청 이력을 남긴다")
    void should_record_payment_attempt_when_payment_requested() {
        given(paymentRepository.findByOrderId(1L)).willReturn(Optional.empty());
        given(paymentRepository.saveAndFlush(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));
        given(attemptRepository.saveAndFlush(any(PaymentAttempt.class))).willAnswer(inv -> inv.getArgument(0));

        paymentService.requestFromPaymentRequested(1L, "ORD-001", new BigDecimal("100.00"));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);

        ArgumentCaptor<PaymentAttempt> attemptCaptor = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(attemptRepository).saveAndFlush(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().getStatus()).isEqualTo(PaymentAttemptStatus.REQUESTED);
        assertThat(attemptCaptor.getValue().getRequestedAt()).isNotNull();

        ArgumentCaptor<PaymentAttemptHistory> historyCaptor = ArgumentCaptor.forClass(PaymentAttemptHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getType()).isEqualTo(PaymentAttemptHistoryType.REQUESTED);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("중복 payment.requested는 새 결제 요청을 만들지 않는다")
    void should_skip_duplicate_payment_requested_when_payment_already_exists() {
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

        paymentService.requestFromPaymentRequested(1L, "ORD-001", new BigDecimal("100.00"));

        verify(paymentRepository, never()).saveAndFlush(any());
        verify(attemptRepository, never()).saveAndFlush(any());
        verify(historyRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("confirm 요청은 요청된 결제 시도를 provider 결제키와 함께 PROCESSING으로 claim한다")
    void should_claim_requested_attempt_for_confirmation() {
        PaymentAttempt attempt = PaymentAttempt.request(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        given(attemptRepository.findFirstByOrderIdAndStatusInOrderByRequestedAtDesc(eq(1L), any()))
                .willReturn(Optional.of(attempt));

        Optional<com.ecommerce.payment.application.dto.PaymentGatewayCommand> command =
                paymentService.claimAttemptForConfirmation(1L, "pay-key-001");

        assertThat(command).isPresent();
        assertThat(command.get().providerPaymentKey()).isEqualTo("pay-key-001");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.PROCESSING);
        assertThat(attempt.getProviderPaymentKey()).isEqualTo("pay-key-001");
        verify(historyRepository).save(argThat(history ->
                history.getType() == PaymentAttemptHistoryType.PROCESSING_STARTED));
    }

    @Test
    @DisplayName("스케줄러용 claim은 retryable 실패 상태만 대상으로 한다")
    void should_claim_only_retryable_attempt_for_scheduler() {
        PaymentAttempt attempt = PaymentAttempt.request(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        attempt.markProcessing("pay-key-001");
        attempt.markRetryableFailed("pg timeout");
        given(attemptRepository.findFirstByStatusInOrderByRequestedAtAsc(any()))
                .willReturn(Optional.of(attempt));

        Optional<com.ecommerce.payment.application.dto.PaymentGatewayCommand> command =
                paymentService.claimNextRetryableAttempt();

        assertThat(command).isPresent();
        verify(attemptRepository).findFirstByStatusInOrderByRequestedAtAsc(argThat(statuses ->
                statuses.contains(PaymentAttemptStatus.RETRYABLE_FAILED)
                        && !statuses.contains(PaymentAttemptStatus.REQUESTED)));
    }

    @Test
    @DisplayName("결제 시도 성공 기록 시 완료 이력과 payment.completed 이벤트를 남긴다")
    void should_complete_attempt_and_publish_event_when_gateway_succeeds() {
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        PaymentAttempt attempt = PaymentAttempt.request(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        attempt.markProcessing();

        given(attemptRepository.findById(10L)).willReturn(Optional.of(attempt));
        given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

        paymentService.completeAttempt(10L, "TXN-001");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.COMPLETED);
        assertThat(attempt.getCompletedAt()).isNotNull();

        ArgumentCaptor<PaymentAttemptHistory> historyCaptor = ArgumentCaptor.forClass(PaymentAttemptHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getType()).isEqualTo(PaymentAttemptHistoryType.COMPLETED);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PaymentCompletedEvent.class);
    }

    @Test
    @DisplayName("결제 시도 실패 기록 시 실패 이력과 payment.failed 이벤트를 남긴다")
    void should_fail_attempt_and_publish_event_when_gateway_rejects() {
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        PaymentAttempt attempt = PaymentAttempt.request(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        attempt.markProcessing();

        given(attemptRepository.findById(10L)).willReturn(Optional.of(attempt));
        given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

        paymentService.failAttempt(10L, "card rejected", false);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);

        ArgumentCaptor<PaymentAttemptHistory> historyCaptor = ArgumentCaptor.forClass(PaymentAttemptHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getType()).isEqualTo(PaymentAttemptHistoryType.FAILED);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PaymentFailedEvent.class);
    }

    @Test
    @DisplayName("COMPLETED 상태 결제가 있으면 REFUNDED로 전이한다")
    void should_refund_completed_payment_when_order_cancelled() {
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        payment.markCompleted("TXN-001");
        given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

        paymentService.cancelFromEvent(1L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("완료된 결제 취소는 환불만 기록하고 완료된 결제 시도는 취소 이력으로 바꾸지 않는다")
    void should_not_cancel_completed_attempt_when_refunding_completed_payment() {
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        payment.markCompleted("TXN-001");
        PaymentAttempt attempt = PaymentAttempt.request(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        attempt.markProcessing();
        attempt.markCompleted("TXN-001");

        given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

        paymentService.cancelFromEvent(1L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.COMPLETED);
        verify(attemptRepository, never()).findFirstByOrderIdOrderByRequestedAtDesc(1L);
        verify(historyRepository, never()).save(argThat(history ->
                history.getType() == PaymentAttemptHistoryType.CANCELLED));
    }

    @Test
    @DisplayName("PENDING 상태 결제가 있으면 FAILED로 전이한다")
    void should_fail_pending_payment_when_order_cancelled() {
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

        paymentService.cancelFromEvent(1L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("취소할 결제가 없으면 아무 작업도 하지 않는다")
    void should_ignore_cancel_when_payment_does_not_exist() {
        given(paymentRepository.findByOrderId(1L)).willReturn(Optional.empty());

        assertThatCode(() -> paymentService.cancelFromEvent(1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("동기 결제 API도 PG 호출 없이 요청 행만 생성한다")
    void should_record_attempt_when_sync_payment_api_is_called() {
        given(paymentRepository.findByOrderId(1L)).willReturn(Optional.empty());
        given(paymentRepository.saveAndFlush(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));
        given(attemptRepository.saveAndFlush(any(PaymentAttempt.class))).willAnswer(inv -> inv.getArgument(0));

        Payment payment = paymentService.process(new ProcessPaymentCommand(
                1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(attemptRepository).saveAndFlush(any(PaymentAttempt.class));
    }
}
