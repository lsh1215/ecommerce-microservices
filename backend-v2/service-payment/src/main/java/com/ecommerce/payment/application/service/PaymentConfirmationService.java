package com.ecommerce.payment.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.payment.PaymentErrorCode;
import com.ecommerce.payment.application.dto.ConfirmPaymentCommand;
import com.ecommerce.payment.application.dto.PaymentGatewayCommand;
import com.ecommerce.payment.application.dto.PaymentGatewayResult;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentConfirmationService {

    private final PaymentService paymentService;
    private final PaymentGatewayPort gatewayPort;

    public Payment confirm(ConfirmPaymentCommand command) {
        PaymentGatewayCommand gatewayCommand = paymentService
                .claimAttemptForConfirmation(command.orderId(), command.providerPaymentKey())
                .orElse(null);
        if (gatewayCommand == null) {
            Payment payment = paymentService.getByOrderId(command.orderId());
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                return payment;
            }
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS,
                    "No confirmable payment attempt for orderId=" + command.orderId());
        }
        return authorize(gatewayCommand);
    }

    private Payment authorize(PaymentGatewayCommand command) {
        try {
            PaymentGatewayResult result = gatewayPort.authorize(command);
            if (result.success()) {
                return paymentService.completeAttempt(command.attemptId(), result.transactionId());
            }
            return paymentService.failAttempt(command.attemptId(), result.reason(), result.retryable());
        } catch (Exception e) {
            log.warn("PG 결제 승인 호출 실패: orderNumber={}, attemptId={}",
                    command.orderNumber(), command.attemptId(), e);
            return paymentService.failAttempt(command.attemptId(), e.getMessage(), true);
        }
    }
}
