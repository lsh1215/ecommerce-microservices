package com.ecommerce.order.api.dto.response;

import com.ecommerce.order.domain.model.ShippingAddress;

public record ShippingAddressResponse(
        String recipientName,
        String phone,
        String zipCode,
        String address1,
        String address2
) {

    public static ShippingAddressResponse from(ShippingAddress address) {
        return new ShippingAddressResponse(
                address.getRecipientName(),
                address.getPhone(),
                address.getZipCode(),
                address.getAddress1(),
                address.getAddress2()
        );
    }
}
