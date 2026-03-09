package com.ecommerce.payment.application.usecase;

import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentEvent;
import com.ecommerce.payment.domain.repository.PaymentEventRepository;
import com.ecommerce.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;

    @Transactional
    public Payment execute(String publicId) {
        Payment payment = paymentRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Payment", publicId));

        paymentEventRepository.save(PaymentEvent.create(
                payment, "REFUND_INITIATED", payment.getAmount(), payment.getCurrency()));

        payment.refund();
        paymentRepository.save(payment);

        paymentEventRepository.save(PaymentEvent.create(
                payment, "REFUNDED", payment.getAmount(), payment.getCurrency()));

        return payment;
    }
}
