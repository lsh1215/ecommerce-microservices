package com.ecommerce.order.domain.service;

import com.ecommerce.order.application.dto.PaymentResult;
import java.math.BigDecimal;

public interface PaymentRequestPort {
    PaymentResult requestPayment(Long orderId, String orderNumber, BigDecimal amount);
}
