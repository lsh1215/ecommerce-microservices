package com.ecommerce.common.dto;

import com.ecommerce.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Generic API response wrapper used for all REST endpoints.
 *
 * @param <T> the type of the response data
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorDetail error;

    private ApiResponse(boolean success, T data, ErrorDetail error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    // Static factory methods

    /**
     * Creates a successful response with data.
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * Creates a successful response with no data.
     */
    public static <Void> ApiResponse<Void> success() {
        return new ApiResponse<>(true, null, null);
    }

    /**
     * Creates an error response with code and message.
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorDetail(code, message));
    }

    /**
     * Creates an error response from an ErrorCode enum value.
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, null, new ErrorDetail(errorCode.getCode(), errorCode.getMessage()));
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public ErrorDetail getError() {
        return error;
    }

    /**
     * Inner record representing error details in the response.
     */
    public record ErrorDetail(String code, String message) {
    }
}
