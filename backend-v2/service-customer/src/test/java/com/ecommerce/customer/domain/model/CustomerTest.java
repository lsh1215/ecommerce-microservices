package com.ecommerce.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CustomerTest {

    @Test
    void createWithValidData() {
        Email email = Email.of("john@example.com");
        Customer customer = Customer.create(email, "password123", "John Doe", "010-1234-5678");

        assertThat(customer.getEmail()).isEqualTo(email);
        assertThat(customer.getName()).isEqualTo("John Doe");
        assertThat(customer.getPhone()).isEqualTo("010-1234-5678");
        assertThat(customer.getPasswordHash()).isNotNull();
        assertThat(customer.getPasswordHash()).isNotEqualTo("password123");
    }

    @Test
    void checkPasswordCorrect() {
        Customer customer = Customer.create(Email.of("a@b.com"), "password123", "Test", null);
        assertThat(customer.checkPassword("password123")).isTrue();
    }

    @Test
    void checkPasswordIncorrect() {
        Customer customer = Customer.create(Email.of("a@b.com"), "password123", "Test", null);
        assertThat(customer.checkPassword("wrongpassword")).isFalse();
    }

    @Test
    void updateProfile() {
        Customer customer = Customer.create(Email.of("a@b.com"), "password123", "Old Name", "old-phone");
        customer.updateProfile("New Name", "new-phone");

        assertThat(customer.getName()).isEqualTo("New Name");
        assertThat(customer.getPhone()).isEqualTo("new-phone");
    }

    @Test
    void updateProfilePartial() {
        Customer customer = Customer.create(Email.of("a@b.com"), "password123", "Name", "phone");
        customer.updateProfile(null, "new-phone");

        assertThat(customer.getName()).isEqualTo("Name");
        assertThat(customer.getPhone()).isEqualTo("new-phone");
    }

    @Test
    void changePassword() {
        Customer customer = Customer.create(Email.of("a@b.com"), "password123", "Test", null);
        customer.changePassword("newpassword123");

        assertThat(customer.checkPassword("newpassword123")).isTrue();
        assertThat(customer.checkPassword("password123")).isFalse();
    }

    @Test
    void changePasswordTooShortThrows() {
        Customer customer = Customer.create(Email.of("a@b.com"), "password123", "Test", null);
        assertThatThrownBy(() -> customer.changePassword("short"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithNullEmailThrows() {
        assertThatThrownBy(() -> Customer.create(null, "password123", "Name", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithShortPasswordThrows() {
        assertThatThrownBy(() -> Customer.create(Email.of("a@b.com"), "short", "Name", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithBlankNameThrows() {
        assertThatThrownBy(() -> Customer.create(Email.of("a@b.com"), "password123", "  ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
