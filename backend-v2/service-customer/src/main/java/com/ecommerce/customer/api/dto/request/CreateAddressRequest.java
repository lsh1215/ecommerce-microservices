package com.ecommerce.customer.api.dto.request;

import com.ecommerce.customer.domain.model.AddressLabel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAddressRequest(
        @NotNull AddressLabel label,
        @NotBlank String recipientName,
        @NotBlank String phone,
        @NotBlank String zipCode,
        @NotBlank String address1,
        String address2,
        boolean isDefault
) {
}
