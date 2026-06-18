package com.ecommerce.order.api.dto.response;

import com.ecommerce.order.application.dto.ShippingAddressResult;

public record ShippingAddressResponse(
        String recipientName,
        String phone,
        String zipCode,
        String address1,
        String address2
) {

    public static ShippingAddressResponse from(ShippingAddressResult address) {
        return new ShippingAddressResponse(
                address.recipientName(),
                address.phone(),
                address.zipCode(),
                address.address1(),
                address.address2()
        );
    }
}
