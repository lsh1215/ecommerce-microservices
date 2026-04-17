package com.ecommerce.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ecommerce.payment.domain.event.PaymentCompletedEvent;
import com.ecommerce.payment.domain.event.PaymentFailedEvent;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentMethod;
import com.ecommerce.payment.domain.model.PaymentStatus;
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
    PaymentStubProcessor stubProcessor;

    @Mock
    ApplicationEventPublisher eventPublisher;

    PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, stubProcessor, eventPublisher, true);
    }

    private PaymentService withGuard(boolean enabled) {
        return new PaymentService(paymentRepository, stubProcessor, eventPublisher, enabled);
    }

    // --- processFromEvent tests ---

    @Test
    @DisplayName("PG 결제 성공 시 Payment를 COMPLETED로 저장하고 PaymentCompletedEvent를 발행한다")
    void processFromEvent_success_completesPaymentAndPublishesEvent() {
        // Given — PG 스텁이 성공을 반환하고 저장소는 아직 처리된 결제가 없음
        given(paymentRepository.existsByOrderIdAndStatus(1L, PaymentStatus.COMPLETED)).willReturn(false);
        given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));
        given(stubProcessor.attempt(any())).willReturn(new PaymentStubProcessor.Result(true, "TXN-001"));

        // When
        paymentService.processFromEvent(1L, "ORD-001", new BigDecimal("100.00"));

        // Then — Payment가 COMPLETED 상태로 저장되고 완료 이벤트가 발행됨
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PaymentCompletedEvent.class);

        PaymentCompletedEvent event = (PaymentCompletedEvent) eventCaptor.getValue();
        assertThat(event.getOrderNumber()).isEqualTo("ORD-001");
        assertThat(event.getTransactionId()).isEqualTo("TXN-001");
    }

    @Test
    @DisplayName("PG 결제 실패 시 Payment를 FAILED로 저장하고 PaymentFailedEvent를 발행한다")
    void processFromEvent_failure_failsPaymentAndPublishesEvent() {
        // Given — PG 스텁이 실패를 반환함
        given(paymentRepository.existsByOrderIdAndStatus(1L, PaymentStatus.COMPLETED)).willReturn(false);
        given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));
        given(stubProcessor.attempt(any())).willReturn(new PaymentStubProcessor.Result(false, null));

        // When
        paymentService.processFromEvent(1L, "ORD-001", new BigDecimal("100.00"));

        // Then — Payment가 FAILED 상태로 저장되고 실패 이벤트가 발행됨
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PaymentFailedEvent.class);
    }

    @Test
    @DisplayName("이미 COMPLETED 결제가 존재하는 주문이면 처리를 건너뛴다")
    void processFromEvent_duplicateOrder_skips() {
        // Given — 이미 완료된 결제가 있는 주문
        given(paymentRepository.existsByOrderIdAndStatus(1L, PaymentStatus.COMPLETED)).willReturn(true);

        // When
        paymentService.processFromEvent(1L, "ORD-001", new BigDecimal("100.00"));

        // Then — 저장, PG 호출, 이벤트 발행 모두 없음
        verify(paymentRepository, never()).save(any());
        verify(stubProcessor, never()).attempt(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // --- cancelFromEvent tests ---

    @Test
    @DisplayName("COMPLETED 상태 결제가 있으면 REFUNDED로 전이한다")
    void cancelFromEvent_completedPayment_refunds() {
        // Given — COMPLETED 상태의 결제가 존재함
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        payment.markCompleted("TXN-001");
        given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

        // When
        paymentService.cancelFromEvent(1L);

        // Then — REFUNDED 상태로 전이됨
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("PENDING 상태 결제가 있으면 FAILED로 전이한다")
    void cancelFromEvent_pendingPayment_fails() {
        // Given — PENDING 상태의 결제가 존재함
        Payment payment = Payment.create(1L, "ORD-001", new BigDecimal("100.00"), PaymentMethod.CARD);
        given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

        // When
        paymentService.cancelFromEvent(1L);

        // Then — FAILED 상태로 전이됨
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("취소할 결제가 없으면 아무 작업도 하지 않는다")
    void cancelFromEvent_noPayment_ignores() {
        // Given — 해당 주문에 결제가 없음
        given(paymentRepository.findByOrderId(1L)).willReturn(Optional.empty());

        // When / Then — 예외 없이 조용히 종료됨
        assertThatCode(() -> paymentService.cancelFromEvent(1L)).doesNotThrowAnyException();
    }

    // --- business-idempotency-guard toggle tests ---

    @Test
    @DisplayName("businessGuard=false 일 때 existsByOrderIdAndStatus 체크 없이 항상 Payment를 생성한다")
    void processFromEvent_guardDisabled_skipsCompletedCheckAndAlwaysProcesses() {
        given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));
        given(stubProcessor.attempt(any())).willReturn(new PaymentStubProcessor.Result(true, "TXN-001"));

        PaymentService disabled = withGuard(false);
        disabled.processFromEvent(1L, "ORD-001", new BigDecimal("100.00"));

        verify(paymentRepository, never()).existsByOrderIdAndStatus(any(), any());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("businessGuard=false 일 때 같은 orderId에 대해 processFromEvent를 두 번 호출하면 Payment가 두 번 저장된다")
    void processFromEvent_guardDisabled_allowsDuplicatePaymentCreation() {
        given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));
        given(stubProcessor.attempt(any())).willReturn(new PaymentStubProcessor.Result(true, "TXN-001"));

        PaymentService disabled = withGuard(false);
        disabled.processFromEvent(1L, "ORD-001", new BigDecimal("100.00"));
        disabled.processFromEvent(1L, "ORD-001", new BigDecimal("100.00"));

        verify(paymentRepository, never()).existsByOrderIdAndStatus(any(), any());
        verify(paymentRepository, org.mockito.Mockito.times(2)).save(any(Payment.class));
    }
}
