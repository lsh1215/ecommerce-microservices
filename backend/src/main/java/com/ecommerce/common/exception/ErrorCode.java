package com.ecommerce.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common 400
    INVALID_INPUT("C001", "Invalid input", HttpStatus.BAD_REQUEST),

    // Common 401
    UNAUTHORIZED("C004", "Unauthorized", HttpStatus.UNAUTHORIZED),

    // Common 403
    FORBIDDEN("C005", "Forbidden", HttpStatus.FORBIDDEN),

    // Common 404
    ENTITY_NOT_FOUND("C002", "Entity not found", HttpStatus.NOT_FOUND),

    // Common 405
    METHOD_NOT_ALLOWED("C007", "Method not allowed", HttpStatus.METHOD_NOT_ALLOWED),

    // Common 409
    DUPLICATE_ENTITY("C006", "Duplicate entity", HttpStatus.CONFLICT),

    // Common 413
    PAYLOAD_TOO_LARGE("C008", "Payload too large", HttpStatus.PAYLOAD_TOO_LARGE),

    // Common 415
    UNSUPPORTED_MEDIA_TYPE("C009", "Unsupported media type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    // Common 500
    INTERNAL_ERROR("C003", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),

    // Customer
    DUPLICATE_EMAIL("CU001", "Email already exists", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS("CU002", "Invalid email or password", HttpStatus.UNAUTHORIZED),

    // Inventory
    INSUFFICIENT_STOCK("IN001", "Insufficient stock", HttpStatus.CONFLICT),
    INVENTORY_LOCK_FAILED("IN002", "Inventory update conflict, please retry", HttpStatus.CONFLICT),

    // Drop
    INVALID_STATUS_TRANSITION("DR001", "Invalid status transition", HttpStatus.BAD_REQUEST),
    DROP_ALLOCATION_EXCEEDED("DR002", "Allocation exceeds available inventory", HttpStatus.BAD_REQUEST),

    // Order
    ORDER_NOT_CANCELLABLE("OR001", "Order cannot be cancelled in current status", HttpStatus.BAD_REQUEST),
    DUPLICATE_ORDER("OR002", "Duplicate order request", HttpStatus.CONFLICT),

    // Payment
    PAYMENT_NOT_REFUNDABLE("PA001", "Payment cannot be refunded in current status", HttpStatus.BAD_REQUEST),
    DUPLICATE_PAYMENT("PA002", "Duplicate payment request", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
