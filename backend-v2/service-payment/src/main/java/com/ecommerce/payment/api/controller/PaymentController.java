package com.ecommerce.payment.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.payment.api.dto.request.ConfirmPaymentRequest;
import com.ecommerce.payment.api.dto.request.ProcessPaymentRequest;
import com.ecommerce.payment.api.dto.request.RefundPaymentRequest;
import com.ecommerce.payment.api.dto.response.PaymentResponse;
import com.ecommerce.payment.application.dto.ConfirmPaymentCommand;
import com.ecommerce.payment.application.dto.ProcessPaymentCommand;
import com.ecommerce.payment.application.dto.RefundPaymentCommand;
import com.ecommerce.payment.application.service.PaymentConfirmationService;
import com.ecommerce.payment.application.service.PaymentService;
import com.ecommerce.payment.domain.model.Payment;
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
    private final PaymentConfirmationService paymentConfirmationService;

    @PostMapping("/process")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentResponse> processPayment(@Valid @RequestBody ProcessPaymentRequest request) {
        ProcessPaymentCommand command = new ProcessPaymentCommand(
                request.orderId(), request.orderNumber(), request.amount(), request.paymentMethod()
        );
        Payment payment = paymentService.process(command);
        return ApiResponse.created(PaymentResponse.from(payment));
    }

    @PostMapping("/confirm")
    public ApiResponse<PaymentResponse> confirmPayment(@Valid @RequestBody ConfirmPaymentRequest request) {
        Payment payment = paymentConfirmationService.confirm(
                new ConfirmPaymentCommand(request.orderId(), request.providerPaymentKey()));
        return ApiResponse.ok(PaymentResponse.from(payment));
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<PaymentResponse> refundPayment(
            @PathVariable Long id,
            @RequestBody(required = false) RefundPaymentRequest request) {
        String reason = request != null ? request.reason() : null;
        RefundPaymentCommand command = new RefundPaymentCommand(reason);
        Payment payment = paymentService.refund(id, command);
        return ApiResponse.ok(PaymentResponse.from(payment));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> getPayment(@PathVariable Long id) {
        Payment payment = paymentService.get(id);
        return ApiResponse.ok(PaymentResponse.from(payment));
    }
}
