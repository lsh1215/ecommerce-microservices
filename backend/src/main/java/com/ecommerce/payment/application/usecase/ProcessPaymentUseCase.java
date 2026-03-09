package com.ecommerce.payment.application.usecase;

import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentEvent;
import com.ecommerce.payment.domain.repository.PaymentEventRepository;
import com.ecommerce.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProcessPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;

    @Transactional
    public Payment execute(Long orderId, BigDecimal amount, String currency,
                           String idempotencyKey, String paymentMethod) {
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        Payment payment = Payment.create(orderId, idempotencyKey, amount, currency, paymentMethod);
        paymentRepository.save(payment);
        paymentEventRepository.save(PaymentEvent.create(payment, "INITIATED", amount, currency));

        payment.complete();
        paymentRepository.save(payment);
        paymentEventRepository.save(PaymentEvent.create(payment, "COMPLETED", amount, currency));

        return payment;
    }
}
