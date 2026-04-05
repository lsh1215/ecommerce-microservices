package com.ecommerce.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CustomerAddressTest {

    private Customer createCustomer() {
        return Customer.create(Email.of("test@example.com"), "password123", "Test User", null);
    }

    @Test
    void markAsDefault() {
        CustomerAddress address = CustomerAddress.create(
                createCustomer(), AddressLabel.HOME, "Recipient", "010-0000-0000",
                "12345", "123 Main St", null, false
        );

        assertThat(address.isDefault()).isFalse();
        address.markAsDefault();
        assertThat(address.isDefault()).isTrue();
    }

    @Test
    void unmarkDefault() {
        CustomerAddress address = CustomerAddress.create(
                createCustomer(), AddressLabel.WORK, "Recipient", "010-0000-0000",
                "12345", "456 Work Ave", null, true
        );

        assertThat(address.isDefault()).isTrue();
        address.unmarkDefault();
        assertThat(address.isDefault()).isFalse();
    }

    @Test
    void update() {
        CustomerAddress address = CustomerAddress.create(
                createCustomer(), AddressLabel.HOME, "Old Name", "010-0000-0000",
                "12345", "Old Address", null, false
        );

        address.update(AddressLabel.WORK, "New Name", "010-1111-1111",
                "54321", "New Address", "Apt 2");

        assertThat(address.getLabel()).isEqualTo(AddressLabel.WORK);
        assertThat(address.getRecipientName()).isEqualTo("New Name");
        assertThat(address.getPhone()).isEqualTo("010-1111-1111");
        assertThat(address.getZipCode()).isEqualTo("54321");
        assertThat(address.getAddress1()).isEqualTo("New Address");
        assertThat(address.getAddress2()).isEqualTo("Apt 2");
    }

    @Test
    void createWithValidData() {
        Customer customer = createCustomer();
        CustomerAddress address = CustomerAddress.create(
                customer, AddressLabel.OTHER, "Name", "010-0000-0000",
                "12345", "Address 1", "Address 2", true
        );

        assertThat(address.getCustomer()).isEqualTo(customer);
        assertThat(address.getLabel()).isEqualTo(AddressLabel.OTHER);
        assertThat(address.getRecipientName()).isEqualTo("Name");
        assertThat(address.isDefault()).isTrue();
    }
}
