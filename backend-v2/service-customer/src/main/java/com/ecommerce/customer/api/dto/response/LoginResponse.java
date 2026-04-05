package com.ecommerce.customer.api.dto.response;

import com.ecommerce.customer.domain.model.Customer;

public record LoginResponse(
        Long customerId,
        String email,
        String name
) {

    public static LoginResponse from(Customer customer) {
        return new LoginResponse(
                customer.getId(),
                customer.getEmail().value(),
                customer.getName()
        );
    }
}
