package com.ecommerce.customer.api.dto.response;

import com.ecommerce.customer.domain.model.AddressLabel;
import com.ecommerce.customer.domain.model.CustomerAddress;

public record AddressResponse(
        Long id,
        AddressLabel label,
        String recipientName,
        String phone,
        String zipCode,
        String address1,
        String address2,
        boolean isDefault
) {

    public static AddressResponse from(CustomerAddress address) {
        return new AddressResponse(
                address.getId(),
                address.getLabel(),
                address.getRecipientName(),
                address.getPhone(),
                address.getZipCode(),
                address.getAddress1(),
                address.getAddress2(),
                address.isDefault()
        );
    }
}
