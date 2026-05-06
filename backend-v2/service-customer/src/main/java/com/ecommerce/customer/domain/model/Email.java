package com.ecommerce.customer.domain.model;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.customer.CustomerErrorCode;
import java.util.regex.Pattern;

public record Email(String value) {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public Email {
        if (value == null || value.isBlank()) {
            throw new BusinessException(CustomerErrorCode.INVALID_EMAIL_FORMAT,
                    "Email must not be blank");
        }
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(CustomerErrorCode.INVALID_EMAIL_FORMAT,
                    "Invalid email format: " + value);
        }
    }

    public static Email of(String raw) {
        return new Email(raw);
    }
}
