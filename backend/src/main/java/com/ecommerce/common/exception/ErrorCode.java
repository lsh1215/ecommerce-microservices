package com.ecommerce.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 400 Bad Request
    INVALID_INPUT("C001", "Invalid input", HttpStatus.BAD_REQUEST),

    // 401 Unauthorized
    UNAUTHORIZED("C004", "Unauthorized", HttpStatus.UNAUTHORIZED),

    // 403 Forbidden
    FORBIDDEN("C005", "Forbidden", HttpStatus.FORBIDDEN),

    // 404 Not Found
    ENTITY_NOT_FOUND("C002", "Entity not found", HttpStatus.NOT_FOUND),

    // 405 Method Not Allowed
    METHOD_NOT_ALLOWED("C007", "Method not allowed", HttpStatus.METHOD_NOT_ALLOWED),

    // 409 Conflict
    DUPLICATE_ENTITY("C006", "Duplicate entity", HttpStatus.CONFLICT),

    // 413 Payload Too Large
    PAYLOAD_TOO_LARGE("C008", "Payload too large", HttpStatus.PAYLOAD_TOO_LARGE),

    // 415 Unsupported Media Type
    UNSUPPORTED_MEDIA_TYPE("C009", "Unsupported media type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    // 500 Internal Server Error
    INTERNAL_ERROR("C003", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
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

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
