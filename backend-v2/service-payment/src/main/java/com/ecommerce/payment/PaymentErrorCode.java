package com.ecommerce.payment;

import com.ecommerce.common.exception.ErrorCodeBase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCodeBase {

    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_001", "Payment not found"),
    INVALID_PAYMENT_STATUS(HttpStatus.BAD_REQUEST, "PAYMENT_002", "Invalid payment status transition"),
    DUPLICATE_PAYMENT(HttpStatus.CONFLICT, "PAYMENT_003", "Payment already exists for this order"),
    INVALID_PAYMENT_DATA(HttpStatus.BAD_REQUEST, "PAYMENT_004", "Invalid payment data");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
