package com.ecommerce.payment.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.payment.api.dto.response.PaymentResponse;
import com.ecommerce.payment.application.service.PaymentService;
import com.ecommerce.payment.domain.model.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/payments")
@RequiredArgsConstructor
public class InternalPaymentController {

    private final PaymentService paymentService;

    @GetMapping("/order/{orderId}")
    public ApiResponse<PaymentResponse> getPaymentByOrderId(@PathVariable Long orderId) {
        Payment payment = paymentService.getByOrderId(orderId);
        return ApiResponse.ok(PaymentResponse.from(payment));
    }
}
