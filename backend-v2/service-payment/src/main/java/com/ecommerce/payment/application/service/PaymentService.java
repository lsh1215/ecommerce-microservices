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
import com.ecommerce.payment.infra.kafka.producer.PaymentEventProducer;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentStubProcessor stubProcessor;
    private final PaymentEventProducer eventProducer;

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

    /**
     * Kafka 이벤트를 통해 비동기로 결제를 처리한다.
     * order.created 이벤트를 수신한 OrderEventConsumer가 호출.
     */
    @Transactional
    public void processFromEvent(Long orderId, String orderNumber, BigDecimal amount) {
        // 멱등성: 이미 처리된 주문이면 무시
        if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.COMPLETED)) {
            log.info("이미 완료된 결제 무시: orderId={}", orderId);
            return;
        }

        Payment payment = Payment.create(orderId, orderNumber, amount, PaymentMethod.CARD);
        paymentRepository.save(payment);

        // 외부 PG사 연동을 가상으로 대체 (90% 성공, 10% 실패 시뮬레이션)
        PaymentStubProcessor.Result result = stubProcessor.attempt(amount);

        if (result.success()) {
            payment.markCompleted(result.transactionId());
            eventProducer.publishPaymentCompleted(new PaymentCompletedEvent(
                    orderNumber, orderId, payment.getId(), result.transactionId(), amount));
        } else {
            payment.markFailed("stub rejection");
            eventProducer.publishPaymentFailed(new PaymentFailedEvent(
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
}
