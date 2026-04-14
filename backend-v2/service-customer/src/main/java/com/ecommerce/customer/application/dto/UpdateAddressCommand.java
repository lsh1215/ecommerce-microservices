package com.ecommerce.customer.application.dto;

import com.ecommerce.customer.domain.model.AddressLabel;

public record UpdateAddressCommand(
        AddressLabel label,
        String recipientName,
        String phone,
        String zipCode,
        String address1,
        String address2
) {}
