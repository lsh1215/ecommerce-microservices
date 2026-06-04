package com.ecommerce.payment.application.service;

import com.ecommerce.payment.application.dto.PaymentGatewayCommand;
import com.ecommerce.payment.application.dto.PaymentGatewayResult;

public interface PaymentGatewayPort {

    PaymentGatewayResult authorize(PaymentGatewayCommand command);
}
