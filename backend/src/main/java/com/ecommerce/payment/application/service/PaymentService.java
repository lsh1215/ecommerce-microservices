package com.ecommerce.payment.application.service;

import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentEvent;
import com.ecommerce.payment.domain.repository.PaymentEventRepository;
import com.ecommerce.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;

    @Transactional(readOnly = true)
    public Payment getByPublicId(String publicId) {
        return paymentRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Payment", publicId));
    }

    @Transactional(readOnly = true)
    public Payment getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Payment for order", orderId));
    }

    @Transactional(readOnly = true)
    public List<PaymentEvent> listPaymentEvents(Long paymentId) {
        if (!paymentRepository.existsById(paymentId)) {
            throw new EntityNotFoundException("Payment", paymentId);
        }
        return paymentEventRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
    }
}
