package com.ecommerce.customer.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.customer.domain.model.Customer;
import com.ecommerce.customer.domain.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void register_shouldCreateCustomerWithHashedPassword() {
        given(customerRepository.existsByEmail("new@example.com")).willReturn(false);
        given(customerRepository.save(any(Customer.class))).willAnswer(invocation -> invocation.getArgument(0));

        Customer result = customerService.register("new@example.com", "password123", "New User");

        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getName()).isEqualTo("New User");
        assertThat(BCrypt.checkpw("password123", result.getPasswordHash())).isTrue();
        assertThat(result.getRole()).isEqualTo("CUSTOMER");
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void register_shouldThrowWhenEmailAlreadyExists() {
        given(customerRepository.existsByEmail("dup@example.com")).willReturn(true);

        assertThatThrownBy(() -> customerService.register("dup@example.com", "password123", "Dup"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_EMAIL));

        verify(customerRepository, never()).save(any());
    }

    @Test
    void login_shouldReturnCustomerWhenCredentialsMatch() {
        String hash = BCrypt.hashpw("correct", BCrypt.gensalt());
        Customer customer = Customer.create("login@example.com", hash, "Login User");
        given(customerRepository.findByEmail("login@example.com")).willReturn(Optional.of(customer));

        Customer result = customerService.login("login@example.com", "correct");

        assertThat(result.getEmail()).isEqualTo("login@example.com");
    }

    @Test
    void login_shouldThrowWhenPasswordDoesNotMatch() {
        String hash = BCrypt.hashpw("correct", BCrypt.gensalt());
        Customer customer = Customer.create("login@example.com", hash, "Login User");
        given(customerRepository.findByEmail("login@example.com")).willReturn(Optional.of(customer));

        assertThatThrownBy(() -> customerService.login("login@example.com", "wrong"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void login_shouldThrowWhenEmailNotFound() {
        given(customerRepository.findByEmail("none@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.login("none@example.com", "any"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void getByPublicId_shouldReturnCustomerWhenExists() {
        Customer customer = Customer.create("get@example.com", "hash", "Get User");
        given(customerRepository.findByPublicId("SOME_ULID")).willReturn(Optional.of(customer));

        Customer result = customerService.getByPublicId("SOME_ULID");

        assertThat(result.getEmail()).isEqualTo("get@example.com");
    }

    @Test
    void getByPublicId_shouldThrowWhenNotFound() {
        given(customerRepository.findByPublicId(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getByPublicId("NONEXISTENT"))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
