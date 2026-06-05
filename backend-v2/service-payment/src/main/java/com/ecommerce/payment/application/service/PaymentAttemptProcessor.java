package com.ecommerce.payment.application.service;

import com.ecommerce.payment.application.dto.PaymentGatewayCommand;
import com.ecommerce.payment.application.dto.PaymentGatewayResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentAttemptProcessor {

    private final PaymentService paymentService;
    private final PaymentGatewayPort gatewayPort;
    private final int maxBatchSize;

    @Autowired
    public PaymentAttemptProcessor(PaymentService paymentService,
                                   PaymentGatewayPort gatewayPort,
                                   @Value("${application.payment-attempt-processor.max-batch-size:50}")
                                   int maxBatchSize) {
        this.paymentService = paymentService;
        this.gatewayPort = gatewayPort;
        this.maxBatchSize = Math.max(1, maxBatchSize);
    }

    PaymentAttemptProcessor(PaymentService paymentService, PaymentGatewayPort gatewayPort) {
        this(paymentService, gatewayPort, 50);
    }

    @Scheduled(fixedDelayString = "${application.payment-attempt-processor.fixed-delay-ms:1000}")
    public void processScheduled() {
        for (int i = 0; i < maxBatchSize; i++) {
            if (!processOne()) {
                return;
            }
        }
    }

    public boolean processOne() {
        return paymentService.claimNextRetryableAttempt()
                .map(this::authorize)
                .orElse(false);
    }

    private boolean authorize(PaymentGatewayCommand command) {
        try {
            PaymentGatewayResult result = gatewayPort.authorize(command);
            if (result.success()) {
                paymentService.completeAttempt(command.attemptId(), result.transactionId());
            } else {
                paymentService.failAttempt(command.attemptId(), result.reason(), result.retryable());
            }
        } catch (Exception e) {
            log.warn("PG 결제 승인 호출 실패: orderNumber={}, attemptId={}",
                    command.orderNumber(), command.attemptId(), e);
            paymentService.failAttempt(command.attemptId(), e.getMessage(), true);
        }
        return true;
    }
}
