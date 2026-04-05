package com.ecommerce.customer.api.dto.response;

import com.ecommerce.customer.domain.model.Customer;
import java.time.LocalDateTime;

public record CustomerResponse(
        Long id,
        String email,
        String name,
        String phone,
        LocalDateTime createdAt
) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getEmail().value(),
                customer.getName(),
                customer.getPhone(),
                customer.getCreatedAt()
        );
    }
}
