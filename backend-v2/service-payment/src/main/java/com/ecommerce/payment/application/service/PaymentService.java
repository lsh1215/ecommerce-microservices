package com.ecommerce.payment.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.payment.PaymentErrorCode;
import com.ecommerce.payment.application.dto.PaymentGatewayCommand;
import com.ecommerce.payment.application.dto.ProcessPaymentCommand;
import com.ecommerce.payment.application.dto.RefundPaymentCommand;
import com.ecommerce.payment.domain.event.PaymentCompletedEvent;
import com.ecommerce.payment.domain.event.PaymentFailedEvent;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentAttempt;
import com.ecommerce.payment.domain.model.PaymentAttemptHistory;
import com.ecommerce.payment.domain.model.PaymentAttemptHistoryType;
import com.ecommerce.payment.domain.model.PaymentAttemptStatus;
import com.ecommerce.payment.domain.model.PaymentMethod;
import com.ecommerce.payment.domain.model.PaymentStatus;
import com.ecommerce.payment.domain.repository.PaymentAttemptHistoryRepository;
import com.ecommerce.payment.domain.repository.PaymentAttemptRepository;
import com.ecommerce.payment.domain.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final List<PaymentAttemptStatus> CONFIRMABLE_STATUSES = List.of(
            PaymentAttemptStatus.REQUESTED,
            PaymentAttemptStatus.RETRYABLE_FAILED
    );
    private static final List<PaymentAttemptStatus> RETRYABLE_STATUSES = List.of(
            PaymentAttemptStatus.RETRYABLE_FAILED
    );

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentAttemptHistoryRepository historyRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Payment process(ProcessPaymentCommand command) {
        return requestPayment(command.orderId(), command.orderNumber(), command.amount(), command.paymentMethod());
    }

    @Transactional
    public void requestFromPaymentRequested(Long orderId, String orderNumber, BigDecimal amount) {
        requestPayment(orderId, orderNumber, amount, PaymentMethod.CARD);
    }

    @Transactional
    public Optional<PaymentGatewayCommand> claimNextRetryableAttempt() {
        Optional<PaymentAttempt> optionalAttempt =
                attemptRepository.findFirstByStatusInOrderByRequestedAtAsc(RETRYABLE_STATUSES);
        return claim(optionalAttempt, null);
    }

    @Transactional
    public Optional<PaymentGatewayCommand> claimAttemptForConfirmation(Long orderId, String providerPaymentKey) {
        Optional<PaymentAttempt> optionalAttempt =
                attemptRepository.findFirstByOrderIdAndStatusInOrderByRequestedAtDesc(orderId, CONFIRMABLE_STATUSES);
        return claim(optionalAttempt, providerPaymentKey);
    }

    private Optional<PaymentGatewayCommand> claim(Optional<PaymentAttempt> optionalAttempt, String providerPaymentKey) {
        if (optionalAttempt.isEmpty()) {
            return Optional.empty();
        }

        PaymentAttempt attempt = optionalAttempt.get();
        attempt.markProcessing(providerPaymentKey);
        historyRepository.save(PaymentAttemptHistory.of(attempt, PaymentAttemptHistoryType.PROCESSING_STARTED));

        return Optional.of(new PaymentGatewayCommand(
                attempt.getId(),
                attempt.getOrderId(),
                attempt.getOrderNumber(),
                attempt.getAmount(),
                attempt.getPaymentMethod(),
                attempt.getIdempotencyKey(),
                attempt.getProviderPaymentKey()
        ));
    }

    @Transactional
    public Payment completeAttempt(Long attemptId, String transactionId) {
        PaymentAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        Payment payment = paymentRepository.findByOrderId(attempt.getOrderId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (attempt.getStatus() == PaymentAttemptStatus.COMPLETED) {
            return payment;
        }

        attempt.markCompleted(transactionId);
        payment.markCompleted(transactionId);
        historyRepository.save(PaymentAttemptHistory.of(
                attempt, PaymentAttemptHistoryType.COMPLETED, transactionId, null));

        eventPublisher.publishEvent(new PaymentCompletedEvent(
                attempt.getOrderNumber(),
                attempt.getOrderId(),
                payment.getId(),
                transactionId,
                attempt.getAmount()
        ));
        return payment;
    }

    @Transactional
    public Payment failAttempt(Long attemptId, String reason, boolean retryable) {
        PaymentAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        Payment payment = paymentRepository.findByOrderId(attempt.getOrderId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (retryable) {
            attempt.markRetryableFailed(reason);
            historyRepository.save(PaymentAttemptHistory.of(
                    attempt, PaymentAttemptHistoryType.RETRYABLE_FAILED, null, reason));
            return payment;
        }

        attempt.markFailed(reason);
        payment.markFailed(reason);
        historyRepository.save(PaymentAttemptHistory.of(
                attempt, PaymentAttemptHistoryType.FAILED, null, reason));

        eventPublisher.publishEvent(new PaymentFailedEvent(
                attempt.getOrderNumber(),
                attempt.getOrderId(),
                reason
        ));
        return payment;
    }

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
            return;
        } else if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.markFailed("order cancelled");
            log.info("결제 실패 처리 (주문 취소): orderId={}, paymentId={}", orderId, payment.getId());
        }

        Optional<PaymentAttempt> optionalAttempt = attemptRepository.findFirstByOrderIdOrderByRequestedAtDesc(orderId);
        optionalAttempt.ifPresent(attempt -> {
            attempt.markCancelled("order cancelled");
            historyRepository.save(PaymentAttemptHistory.of(
                    attempt, PaymentAttemptHistoryType.CANCELLED, null, "order cancelled"));
        });
    }

    private Payment requestPayment(Long orderId, String orderNumber, BigDecimal amount, PaymentMethod method) {
        Optional<Payment> existing = paymentRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            log.info("이미 생성된 결제 요청 무시: orderId={}", orderId);
            return existing.get();
        }

        Payment payment = Payment.create(orderId, orderNumber, amount, method);
        Payment savedPayment = paymentRepository.saveAndFlush(payment);
        PaymentAttempt attempt = PaymentAttempt.request(orderId, orderNumber, amount, method);
        PaymentAttempt savedAttempt = attemptRepository.saveAndFlush(attempt);
        historyRepository.save(PaymentAttemptHistory.of(savedAttempt, PaymentAttemptHistoryType.REQUESTED));
        return savedPayment;
    }
}
