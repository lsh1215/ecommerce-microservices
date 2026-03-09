package com.ecommerce.customer.api.dto.response;

import com.ecommerce.customer.domain.model.CustomerAddress;

import java.time.LocalDateTime;

public record AddressResponse(
        String publicId,
        String label,
        String recipientName,
        String phone,
        String street,
        String detail,
        String city,
        String stateProvince,
        String postalCode,
        String country,
        boolean isDefault,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AddressResponse from(CustomerAddress address) {
        return new AddressResponse(
                address.getPublicId(),
                address.getLabel(),
                address.getRecipientName(),
                address.getPhone(),
                address.getStreet(),
                address.getDetail(),
                address.getCity(),
                address.getStateProvince(),
                address.getPostalCode(),
                address.getCountry(),
                address.isDefault(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }
}
