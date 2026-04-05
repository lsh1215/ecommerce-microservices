package com.ecommerce.payment.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.payment.PaymentErrorCode;
import com.ecommerce.payment.api.dto.request.ProcessPaymentRequest;
import com.ecommerce.payment.api.dto.request.RefundPaymentRequest;
import com.ecommerce.payment.api.dto.response.PaymentResponse;
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

    @Transactional
    public PaymentResponse process(ProcessPaymentRequest request) {
        if (paymentRepository.existsByOrderIdAndStatus(request.orderId(), PaymentStatus.COMPLETED)) {
            throw new BusinessException(PaymentErrorCode.DUPLICATE_PAYMENT);
        }

        Payment payment = Payment.create(
                request.orderId(),
                request.orderNumber(),
                request.amount(),
                request.paymentMethod()
        );
        paymentRepository.save(payment);

        PaymentStubProcessor.Result result = stubProcessor.attempt(request.amount());
        if (result.success()) {
            payment.markCompleted(result.transactionId());
        } else {
            payment.markFailed("stub rejection");
        }

        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse refund(Long paymentId, RefundPaymentRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        payment.markRefunded();
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        return PaymentResponse.from(payment);
    }
}
