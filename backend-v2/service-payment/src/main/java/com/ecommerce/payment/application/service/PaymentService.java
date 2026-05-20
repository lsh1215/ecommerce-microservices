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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentStubProcessor stubProcessor;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean businessGuardEnabled;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentStubProcessor stubProcessor,
            ApplicationEventPublisher eventPublisher,
            @Value("${application.business-idempotency-guard.enabled:true}") boolean businessGuardEnabled) {
        this.paymentRepository = paymentRepository;
        this.stubProcessor = stubProcessor;
        this.eventPublisher = eventPublisher;
        this.businessGuardEnabled = businessGuardEnabled;
    }

    /** Processes a payment command and records the stub processor outcome. */
    @Transactional
    public Payment process(ProcessPaymentCommand command) {
        if (paymentRepository.existsByOrderIdAndStatus(command.orderId(), PaymentStatus.COMPLETED)) {
            throw new BusinessException(PaymentErrorCode.DUPLICATE_PAYMENT);
        }

        Payment payment = Payment.create(
                command.orderId(),
                command.orderNumber(),
                command.amount(),
                command.paymentMethod()
        );
        paymentRepository.save(payment);

        PaymentStubProcessor.Result result = stubProcessor.attempt(command.amount());

        if (result.success()) {
            payment.markCompleted(result.transactionId());
        } else {
            payment.markFailed("stub rejection");
        }

        return payment;
    }

    /** Refunds a completed payment. */
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

    /** Processes an order-created event from Kafka. */
    @Transactional
    public void processFromEvent(Long orderId, String orderNumber, BigDecimal amount) {
        if (businessGuardEnabled) {
            if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.COMPLETED)) {
                log.info("이미 완료된 결제 무시: orderId={}", orderId);
                return;
            }
        } else {
            log.warn("business idempotency guard disabled — proceeding without completed-payment check: orderId={}", orderId);
        }

        Payment payment = Payment.create(orderId, orderNumber, amount, PaymentMethod.CARD);
        paymentRepository.save(payment);

        PaymentStubProcessor.Result result = stubProcessor.attempt(amount);

        if (result.success()) {
            payment.markCompleted(result.transactionId());
            eventPublisher.publishEvent(new PaymentCompletedEvent(
                    orderNumber, orderId, payment.getId(), result.transactionId(), amount));
        } else {
            payment.markFailed("stub rejection");
            eventPublisher.publishEvent(new PaymentFailedEvent(
                    orderNumber, orderId, "stub rejection"));
        }
    }

    /** Applies payment cancellation or refund after an order-cancelled event. */
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
}
