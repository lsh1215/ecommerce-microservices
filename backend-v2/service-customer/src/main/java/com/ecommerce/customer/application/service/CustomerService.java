package com.ecommerce.customer.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.customer.CustomerErrorCode;
import com.ecommerce.customer.api.dto.request.LoginRequest;
import com.ecommerce.customer.api.dto.request.RegisterCustomerRequest;
import com.ecommerce.customer.api.dto.request.UpdateCustomerRequest;
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

    @Transactional
    public Customer register(RegisterCustomerRequest request) {
        Email email = new Email(request.email());
        if (customerRepository.existsByEmail(email)) {
            throw new BusinessException(CustomerErrorCode.DUPLICATE_EMAIL);
        }
        Customer customer = Customer.create(email, request.password(), request.name(), request.phone());
        return customerRepository.save(customer);
    }

    public Customer login(LoginRequest request) {
        Email email = new Email(request.email());
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.INVALID_CREDENTIALS));
        if (!customer.checkPassword(request.password())) {
            throw new BusinessException(CustomerErrorCode.INVALID_CREDENTIALS);
        }
        return customer;
    }

    public Customer getProfile(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));
    }

    @Transactional
    public Customer updateProfile(Long customerId, UpdateCustomerRequest request) {
        Customer customer = getProfile(customerId);
        customer.updateProfile(request.name(), request.phone());
        return customer;
    }
}
