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
     * 주어진 주문에 대한 결제를 처리한다.
     * Payment를 PENDING 상태로 생성하고, 스텁 프로세서를 실행한 뒤 COMPLETED 또는 FAILED로 전이한다.
     */
    @Transactional
    public Payment process(ProcessPaymentCommand command) {
        // 가드: 동일 주문에 대한 중복 결제 방지
        if (paymentRepository.existsByOrderIdAndStatus(command.orderId(), PaymentStatus.COMPLETED)) {
            throw new BusinessException(PaymentErrorCode.DUPLICATE_PAYMENT);
        }

        // PENDING 상태로 Payment 생성
        Payment payment = Payment.create(
                command.orderId(),
                command.orderNumber(),
                command.amount(),
                command.paymentMethod()
        );
        paymentRepository.save(payment);

        // 외부 PG사 연동을 가상으로 대체 (90% 성공, 10% 실패 시뮬레이션)
        PaymentStubProcessor.Result result = stubProcessor.attempt(command.amount());

        // 프로세서 결과에 따라 상태 전이
        if (result.success()) {
            payment.markCompleted(result.transactionId());
        } else {
            payment.markFailed("stub rejection");
        }

        return payment;
    }

    /**
     * 완료된 결제를 환불 처리한다.
     * 제공된 사유와 관계없이 Payment를 REFUNDED 상태로 전이한다.
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
