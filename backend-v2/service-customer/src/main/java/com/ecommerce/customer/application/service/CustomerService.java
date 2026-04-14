package com.ecommerce.customer.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.customer.CustomerErrorCode;
import com.ecommerce.customer.application.dto.LoginCommand;
import com.ecommerce.customer.application.dto.RegisterCustomerCommand;
import com.ecommerce.customer.application.dto.UpdateCustomerCommand;
import com.ecommerce.customer.domain.model.Customer;
import com.ecommerce.customer.domain.model.Email;
import com.ecommerce.customer.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;

    /**
     * Register a new customer account.
     * Validates email uniqueness before persisting.
     */
    @Transactional
    public Customer register(RegisterCustomerCommand command) {
        Email email = new Email(command.email());
        // Guard: prevent duplicate email registration
        if (customerRepository.existsByEmail(email)) {
            throw new BusinessException(CustomerErrorCode.DUPLICATE_EMAIL);
        }
        Customer customer = Customer.create(email, command.password(), command.name(), command.phone());
        return customerRepository.save(customer);
    }

    /**
     * Authenticate customer by email and password.
     * Returns the Customer entity on success; throws on invalid credentials.
     */
    public Customer login(LoginCommand command) {
        Email email = new Email(command.email());
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.INVALID_CREDENTIALS));
        if (!customer.checkPassword(command.password())) {
            throw new BusinessException(CustomerErrorCode.INVALID_CREDENTIALS);
        }
        return customer;
    }

    public Customer getProfile(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));
    }

    @Transactional
    public Customer updateProfile(Long customerId, UpdateCustomerCommand command) {
        Customer customer = getProfile(customerId);
        customer.updateProfile(command.name(), command.phone());
        return customer;
    }
}
