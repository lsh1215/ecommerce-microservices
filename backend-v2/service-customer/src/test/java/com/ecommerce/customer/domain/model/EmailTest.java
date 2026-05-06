package com.ecommerce.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class EmailTest {

    @Test
    void validEmail() {
        Email email = new Email("user@example.com");
        assertThat(email.value()).isEqualTo("user@example.com");
    }

    @Test
    void validEmailViaFactory() {
        Email email = Email.of("test@domain.co.kr");
        assertThat(email.value()).isEqualTo("test@domain.co.kr");
    }

    @Test
    void nullEmailThrows() {
        assertThatThrownBy(() -> new Email(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void blankEmailThrows() {
        assertThatThrownBy(() -> new Email("  "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void invalidFormatNoAtThrows() {
        assertThatThrownBy(() -> new Email("foo"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void invalidFormatNoLocalPartThrows() {
        assertThatThrownBy(() -> new Email("@bar.com"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void invalidFormatNoDomainThrows() {
        assertThatThrownBy(() -> new Email("foo@"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void equalityForSameValue() {
        Email a = new Email("user@example.com");
        Email b = new Email("user@example.com");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void inequalityForDifferentValue() {
        Email a = new Email("a@example.com");
        Email b = new Email("b@example.com");
        assertThat(a).isNotEqualTo(b);
    }
}
