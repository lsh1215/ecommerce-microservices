package com.ecommerce.customer.api.dto.response;

import com.ecommerce.customer.domain.model.Customer;

public record LoginResponse(
        Long id,
        String publicId,
        String name,
        String email
) {
    public static LoginResponse from(Customer customer) {
        return new LoginResponse(
                customer.getId(),
                customer.getPublicId(),
                customer.getName(),
                customer.getEmail()
        );
    }
}
