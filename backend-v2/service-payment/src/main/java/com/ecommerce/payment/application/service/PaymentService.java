package com.ecommerce.payment.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.payment.PaymentErrorCode;
import com.ecommerce.payment.application.dto.ProcessPaymentCommand;
import com.ecommerce.payment.application.dto.RefundPaymentCommand;
import com.ecommerce.payment.domain.event.PaymentCompletedEvent;
import com.ecommerce.payment.domain.event.PaymentFailedEvent;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentMethod;
import com.ecommerce.payment.domain.model.PaymentStatus;
import com.ecommerce.payment.domain.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentStubProcessor stubProcessor;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Payment process(ProcessPaymentCommand command) {
        if (paymentRepository.findByOrderId(command.orderId()).isPresent()) {
            throw new BusinessException(PaymentErrorCode.DUPLICATE_PAYMENT);
        }

        Payment payment = Payment.create(
                command.orderId(),
                command.orderNumber(),
                command.amount(),
                command.paymentMethod()
        );
        // PG 시도 전에 동일 orderId unique 제약 위반을 확정해 중복 외부 처리를 막는다.
        paymentRepository.saveAndFlush(payment);

        PaymentStubProcessor.Result result = stubProcessor.attempt(command.amount());
        applyProcessorResult(payment, result);

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

    @Transactional
    public void processFromEvent(Long orderId, String orderNumber, BigDecimal amount) {
        if (paymentRepository.findByOrderId(orderId).isPresent()) {
            log.info("Duplicate payment request ignored: orderId={}", orderId);
            return;
        }

        Payment payment = Payment.create(orderId, orderNumber, amount, PaymentMethod.CARD);
        try {
            // Kafka 재처리나 동시 consumer 경합에서도 PG 시도 전에 동일 주문 결제 선점을 확정한다.
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            if (isOrderIdUniqueViolation(e)) {
                log.info("Concurrent duplicate payment request ignored: orderId={}", orderId);
                return;
            }
            throw e;
        }
        PaymentStubProcessor.Result result = stubProcessor.attempt(amount);
        applyProcessorResult(payment, result);

        if (result.success()) {
            eventPublisher.publishEvent(new PaymentCompletedEvent(
                    orderNumber, orderId, payment.getId(), result.transactionId(), amount));
        } else {
            eventPublisher.publishEvent(new PaymentFailedEvent(
                    orderNumber, orderId, "stub rejection"));
        }
    }

    /**
     * 주문 취소 이벤트 수신 시 결제를 취소하거나 환불 처리한다.
     * COMPLETED 상태면 환불, PENDING 상태면 실패 처리.
     */
    @Transactional
    public void cancelFromEvent(Long orderId) {
        Optional<Payment> optionalPayment = paymentRepository.findByOrderId(orderId);
        if (optionalPayment.isEmpty()) {
            log.info("취소할 결제 없음: orderId={}", orderId);
            return;
        }

        Payment payment = optionalPayment.get();
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            payment.markRefunded();
            log.info("결제 환불 처리: orderId={}, paymentId={}", orderId, payment.getId());
        } else if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.markFailed("order cancelled");
            log.info("결제 실패 처리 (주문 취소): orderId={}, paymentId={}", orderId, payment.getId());
        }
    }

    private void applyProcessorResult(Payment payment, PaymentStubProcessor.Result result) {
        if (result.success()) {
            payment.markCompleted(result.transactionId());
        } else {
            payment.markFailed("stub rejection");
        }
    }

    private boolean isOrderIdUniqueViolation(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause() != null
                ? e.getMostSpecificCause().getMessage()
                : e.getMessage();
        return message != null
                && (message.contains("uk_payment_order_id") || message.contains("order_id"));
    }
}
