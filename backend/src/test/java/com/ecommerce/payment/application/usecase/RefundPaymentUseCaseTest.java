package com.ecommerce.payment.application.usecase;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentEvent;
import com.ecommerce.payment.domain.repository.PaymentEventRepository;
import com.ecommerce.payment.domain.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundPaymentUseCaseTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentEventRepository paymentEventRepository;

    @InjectMocks
    private RefundPaymentUseCase refundPaymentUseCase;

    @Test
    void execute_shouldRefundCompletedPayment() {
        Payment payment = Payment.create(1L, "idem-ref", BigDecimal.valueOf(100), "USD", "CARD");
        payment.complete();
        when(paymentRepository.findByPublicId("pub-001")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = refundPaymentUseCase.execute("pub-001");

        assertThat(result.getStatus()).isEqualTo("REFUNDED");

        verify(paymentRepository).save(payment);

        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(paymentEventRepository, times(2)).save(eventCaptor.capture());

        assertThat(eventCaptor.getAllValues().get(0).getEventType()).isEqualTo("REFUND_INITIATED");
        assertThat(eventCaptor.getAllValues().get(1).getEventType()).isEqualTo("REFUNDED");
    }

    @Test
    void execute_shouldThrowWhenPaymentNotFound() {
        when(paymentRepository.findByPublicId("non-existent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refundPaymentUseCase.execute("non-existent"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void execute_shouldThrowWhenPaymentNotRefundable() {
        Payment payment = Payment.create(1L, "idem-pend", BigDecimal.valueOf(100), "USD", null);
        when(paymentRepository.findByPublicId("pub-pending")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> refundPaymentUseCase.execute("pub-pending"))
                .isInstanceOf(BusinessException.class);
    }
}
