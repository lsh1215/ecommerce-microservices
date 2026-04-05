package com.ecommerce.payment.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.payment.api.dto.request.ProcessPaymentRequest;
import com.ecommerce.payment.api.dto.request.RefundPaymentRequest;
import com.ecommerce.payment.api.dto.response.PaymentResponse;
import com.ecommerce.payment.application.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/process")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentResponse> processPayment(@Valid @RequestBody ProcessPaymentRequest request) {
        return ApiResponse.created(paymentService.process(request));
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<PaymentResponse> refundPayment(
            @PathVariable Long id,
            @RequestBody(required = false) RefundPaymentRequest request) {
        RefundPaymentRequest refundRequest = request != null ? request : new RefundPaymentRequest(null);
        return ApiResponse.ok(paymentService.refund(id, refundRequest));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> getPayment(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.get(id));
    }
}
