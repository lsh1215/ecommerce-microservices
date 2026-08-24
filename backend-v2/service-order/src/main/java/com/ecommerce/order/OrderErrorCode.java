package com.ecommerce.order;

import com.ecommerce.common.exception.ErrorCodeBase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCodeBase {

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_001", "Order not found"),
    INVALID_ORDER_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "ORDER_002", "Invalid order status transition"),
    STOCK_RESERVATION_FAILED(HttpStatus.BAD_REQUEST, "ORDER_004", "Stock reservation failed"),
    ORDER_CANCEL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "ORDER_005", "Order cancellation not allowed"),
    PRODUCT_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "ORDER_006", "Product service unavailable"),
    PAYMENT_FAILED(HttpStatus.BAD_GATEWAY, "ORDER_007", "Payment processing failed"),
    INVALID_ORDER_ITEM(HttpStatus.BAD_REQUEST, "ORDER_008", "Invalid order item data"),
    FLASH_SOLD_OUT(HttpStatus.CONFLICT, "ORDER_009", "재고가 소진되었습니다."),
    FLASH_SUBMIT_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "ORDER_010", "접수에 실패했습니다. 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
