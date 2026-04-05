package com.ecommerce.order.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ShippingAddressRequest(
        @NotBlank String recipientName,
        @NotBlank String phone,
        @NotBlank String zipCode,
        @NotBlank String address1,
        String address2
) {
}
