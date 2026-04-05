package com.ecommerce.order.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class ShippingAddress {

    @Column(nullable = false)
    private String recipientName;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String zipCode;

    @Column(nullable = false)
    private String address1;

    private String address2;

    public ShippingAddress(String recipientName, String phone, String zipCode,
                           String address1, String address2) {
        this.recipientName = recipientName;
        this.phone = phone;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShippingAddress that)) return false;
        return Objects.equals(recipientName, that.recipientName)
                && Objects.equals(phone, that.phone)
                && Objects.equals(zipCode, that.zipCode)
                && Objects.equals(address1, that.address1)
                && Objects.equals(address2, that.address2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipientName, phone, zipCode, address1, address2);
    }
}
