package com.ecommerce.customer.api.dto.request;

import com.ecommerce.customer.domain.model.AddressLabel;

public record UpdateAddressRequest(
        AddressLabel label,
        String recipientName,
        String phone,
        String zipCode,
        String address1,
        String address2
) {
}
