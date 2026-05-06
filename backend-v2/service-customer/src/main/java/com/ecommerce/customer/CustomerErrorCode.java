package com.ecommerce.customer;

import com.ecommerce.common.exception.ErrorCodeBase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CustomerErrorCode implements ErrorCodeBase {

    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "CUSTOMER_001", "Customer not found"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "CUSTOMER_002", "Email already exists"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "CUSTOMER_003", "Invalid email or password"),
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "CUSTOMER_004", "Address not found"),
    INVALID_CUSTOMER_DATA(HttpStatus.BAD_REQUEST, "CUSTOMER_005", "Invalid customer data"),
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "CUSTOMER_006", "Invalid email format"),
    INVALID_ADDRESS_DATA(HttpStatus.BAD_REQUEST, "CUSTOMER_007", "Invalid address data");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
