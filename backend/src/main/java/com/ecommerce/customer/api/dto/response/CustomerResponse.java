package com.ecommerce.customer.api.dto.response;

import com.ecommerce.customer.domain.model.Customer;

import java.time.LocalDateTime;

public record CustomerResponse(
        String publicId,
        String email,
        String name,
        String preferredCurrency,
        String preferredLocale,
        String role,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getPublicId(),
                customer.getEmail(),
                customer.getName(),
                customer.getPreferredCurrency(),
                customer.getPreferredLocale(),
                customer.getRole(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
