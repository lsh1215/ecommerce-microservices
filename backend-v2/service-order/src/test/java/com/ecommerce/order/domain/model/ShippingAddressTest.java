package com.ecommerce.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShippingAddressTest {

    @Test
    void equalsByValue() {
        ShippingAddress a1 = new ShippingAddress("John", "010-1234-5678", "12345", "123 Main St", "Apt 1");
        ShippingAddress a2 = new ShippingAddress("John", "010-1234-5678", "12345", "123 Main St", "Apt 1");

        assertThat(a1).isEqualTo(a2);
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
    }

    @Test
    void notEqual_whenFieldsDiffer() {
        ShippingAddress a1 = new ShippingAddress("John", "010-1234-5678", "12345", "123 Main St", "Apt 1");
        ShippingAddress a2 = new ShippingAddress("Jane", "010-1234-5678", "12345", "123 Main St", "Apt 1");

        assertThat(a1).isNotEqualTo(a2);
    }

    @Test
    void equalsByValue_withNullAddress2() {
        ShippingAddress a1 = new ShippingAddress("John", "010-1234-5678", "12345", "123 Main St", null);
        ShippingAddress a2 = new ShippingAddress("John", "010-1234-5678", "12345", "123 Main St", null);

        assertThat(a1).isEqualTo(a2);
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
    }
}
