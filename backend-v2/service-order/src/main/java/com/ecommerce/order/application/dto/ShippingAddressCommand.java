package com.ecommerce.order.application.dto;

public record ShippingAddressCommand(
        String recipientName,
        String phone,
        String zipCode,
        String address1,
        String address2
) {}
