package com.ecommerce.payment.application.service;

import com.ecommerce.payment.application.dto.PaymentGatewayCommand;
import com.ecommerce.payment.application.dto.PaymentGatewayResult;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class PaymentStubProcessor implements PaymentGatewayPort {

    private static final double SUCCESS_RATE = 0.9;

    private final RandomGenerator rng;

    public PaymentStubProcessor() {
        // ThreadLocalRandom is in java.base; avoids the jdk.random module
        // absent from minimal Temurin JRE images.
        this.rng = ThreadLocalRandom.current();
    }

    public PaymentStubProcessor(RandomGenerator rng) {
        this.rng = rng;
    }

    public Result attempt(BigDecimal amount) {
        boolean success = rng.nextDouble() < SUCCESS_RATE;
        String transactionId = success ? UUID.randomUUID().toString() : null;
        return new Result(success, transactionId);
    }

    @Override
    public PaymentGatewayResult authorize(PaymentGatewayCommand command) {
        Result result = attempt(command.amount());
        if (result.success()) {
            return PaymentGatewayResult.success(result.transactionId());
        }
        return PaymentGatewayResult.failure("stub rejection");
    }

    public record Result(boolean success, String transactionId) {}
}
