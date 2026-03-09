package com.ecommerce.order.infra;

import com.ecommerce.order.domain.service.PaymentProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class StubPaymentProcessor implements PaymentProcessor {

    @Override
    public boolean processPayment(Long orderId, BigDecimal amount, String currency) {
        return true;
    }
}
