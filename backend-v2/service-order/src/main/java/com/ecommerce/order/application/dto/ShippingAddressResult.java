package com.ecommerce.order.application.dto;

import com.ecommerce.order.domain.model.ShippingAddress;

public record ShippingAddressResult(
        String recipientName,
        String phone,
        String zipCode,
        String address1,
        String address2
) {

    public static ShippingAddressResult from(ShippingAddress address) {
        return new ShippingAddressResult(
                address.getRecipientName(),
                address.getPhone(),
                address.getZipCode(),
                address.getAddress1(),
                address.getAddress2()
        );
    }
}
