package com.ecommerce.product;

import com.ecommerce.common.exception.ErrorCodeBase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCodeBase {

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_001", "Product not found"),
    VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_002", "Product variant not found"),
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_003", "Brand not found"),
    DUPLICATE_SKU(HttpStatus.CONFLICT, "PRODUCT_004", "SKU already exists"),
    DUPLICATE_BRAND(HttpStatus.CONFLICT, "PRODUCT_005", "Brand name already exists"),
    INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "PRODUCT_006", "Insufficient stock"),
    INVALID_PRODUCT_DATA(HttpStatus.BAD_REQUEST, "PRODUCT_007", "Invalid product data"),
    INVALID_BRAND_DATA(HttpStatus.BAD_REQUEST, "PRODUCT_008", "Invalid brand data"),
    INVALID_VARIANT_OPERATION(HttpStatus.BAD_REQUEST, "PRODUCT_009", "Invalid variant operation");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
