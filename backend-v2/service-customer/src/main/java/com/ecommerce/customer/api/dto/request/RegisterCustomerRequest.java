package com.ecommerce.customer.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterCustomerRequest(
        @NotBlank @jakarta.validation.constraints.Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String name,
        String phone
) {
}
