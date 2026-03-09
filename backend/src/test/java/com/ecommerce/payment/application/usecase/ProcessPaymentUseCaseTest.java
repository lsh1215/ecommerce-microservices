package com.ecommerce.payment.application.usecase;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessPaymentUseCaseTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentEventRepository paymentEventRepository;

    @InjectMocks
    private ProcessPaymentUseCase processPaymentUseCase;

    @Test
    void execute_shouldCreatePaymentAndCompleteIt() {
        when(paymentRepository.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = processPaymentUseCase.execute(1L, BigDecimal.valueOf(29900), "KRW", "idem-001", "CARD");

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getOrderId()).isEqualTo(1L);
        assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(29900));
        assertThat(result.getCurrency()).isEqualTo("KRW");

        verify(paymentRepository, times(2)).save(any(Payment.class));

        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(paymentEventRepository, times(2)).save(eventCaptor.capture());

        assertThat(eventCaptor.getAllValues().get(0).getEventType()).isEqualTo("INITIATED");
        assertThat(eventCaptor.getAllValues().get(1).getEventType()).isEqualTo("COMPLETED");
    }

    @Test
    void execute_shouldReturnExistingPaymentForDuplicateIdempotencyKey() {
        Payment existing = Payment.create(1L, "idem-dup", BigDecimal.valueOf(100), "USD", "CARD");
        existing.complete();
        when(paymentRepository.findByIdempotencyKey("idem-dup")).thenReturn(Optional.of(existing));

        Payment result = processPaymentUseCase.execute(1L, BigDecimal.valueOf(100), "USD", "idem-dup", "CARD");

        assertThat(result).isSameAs(existing);
        verify(paymentRepository, times(0)).save(any());
        verify(paymentEventRepository, times(0)).save(any());
    }
}
