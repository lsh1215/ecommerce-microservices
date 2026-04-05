package com.ecommerce.customer.api.dto.request;

public record UpdateCustomerRequest(
        String name,
        String phone
) {
}
