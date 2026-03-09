package com.ecommerce.order.domain.service;

import java.math.BigDecimal;

public interface PaymentProcessor {

    boolean processPayment(Long orderId, BigDecimal amount, String currency);
}
