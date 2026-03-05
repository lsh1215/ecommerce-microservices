package com.ecommerce.common.exception;

/**
 * Enum of application-level error codes.
 * Each entry maps to a unique code string, human-readable message, and HTTP status.
 */
public enum ErrorCode {

    INVALID_INPUT("C001", "Invalid input", 400),
    ENTITY_NOT_FOUND("C002", "Entity not found", 404),
    INTERNAL_ERROR("C003", "Internal server error", 500),
    UNAUTHORIZED("C004", "Unauthorized", 401),
    FORBIDDEN("C005", "Forbidden", 403),
    DUPLICATE_ENTITY("C006", "Duplicate entity", 409);

    private final String code;
    private final String message;
    private final int httpStatus;

    ErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
