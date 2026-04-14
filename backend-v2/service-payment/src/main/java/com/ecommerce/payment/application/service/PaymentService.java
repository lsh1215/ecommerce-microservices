package com.ecommerce.payment.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.payment.PaymentErrorCode;
import com.ecommerce.payment.application.dto.ProcessPaymentCommand;
import com.ecommerce.payment.application.dto.RefundPaymentCommand;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentStatus;
import com.ecommerce.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentStubProcessor stubProcessor;

    /**
     * Process a payment for the given order.
     * Creates a Payment in PENDING state, runs stub processor, transitions to COMPLETED or FAILED.
     */
    @Transactional
    public Payment process(ProcessPaymentCommand command) {
        // Guard: prevent duplicate payment for the same order
        if (paymentRepository.existsByOrderIdAndStatus(command.orderId(), PaymentStatus.COMPLETED)) {
            throw new BusinessException(PaymentErrorCode.DUPLICATE_PAYMENT);
        }

        // Create payment in PENDING state
        Payment payment = Payment.create(
                command.orderId(),
                command.orderNumber(),
                command.amount(),
                command.paymentMethod()
        );
        paymentRepository.save(payment);

        // Attempt payment via stub processor (simulated gateway, 90% success rate)
        PaymentStubProcessor.Result result = stubProcessor.attempt(command.amount());

        // Transition status based on processor result
        if (result.success()) {
            payment.markCompleted(result.transactionId());
        } else {
            payment.markFailed("stub rejection");
        }

        return payment;
    }

    /**
     * Refund a completed payment.
     * Transitions payment to REFUNDED state regardless of the reason provided.
     */
    @Transactional
    public Payment refund(Long paymentId, RefundPaymentCommand command) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        payment.markRefunded();
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment get(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Payment getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }
}
