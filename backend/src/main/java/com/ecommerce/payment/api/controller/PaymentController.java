package com.ecommerce.payment.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.payment.api.dto.request.ProcessPaymentRequest;
import com.ecommerce.payment.api.dto.request.RefundPaymentRequest;
import com.ecommerce.payment.api.dto.response.PaymentResponse;
import com.ecommerce.payment.application.service.PaymentService;
import com.ecommerce.payment.application.usecase.ProcessPaymentUseCase;
import com.ecommerce.payment.application.usecase.RefundPaymentUseCase;
import com.ecommerce.payment.domain.model.Payment;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final ProcessPaymentUseCase processPaymentUseCase;
    private final RefundPaymentUseCase refundPaymentUseCase;
    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @Valid @RequestBody ProcessPaymentRequest request) {
        Payment payment = processPaymentUseCase.execute(
                request.orderId(), request.amount(), request.currency(),
                request.idempotencyKey(), request.paymentMethod());
        return ResponseEntity.ok(ApiResponse.success(PaymentResponse.from(payment)));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByPublicId(@PathVariable String publicId) {
        Payment payment = paymentService.getByPublicId(publicId);
        return ResponseEntity.ok(ApiResponse.success(PaymentResponse.from(payment)));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByOrderId(@PathVariable Long orderId) {
        Payment payment = paymentService.getByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.success(PaymentResponse.from(payment)));
    }

    @PostMapping("/{publicId}/refund")
    public ResponseEntity<ApiResponse<PaymentResponse>> refundPayment(
            @PathVariable String publicId,
            @RequestBody RefundPaymentRequest request) {
        Payment payment = refundPaymentUseCase.execute(publicId);
        return ResponseEntity.ok(ApiResponse.success(PaymentResponse.from(payment)));
    }
}
