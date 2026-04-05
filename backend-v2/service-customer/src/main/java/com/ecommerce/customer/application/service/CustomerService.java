package com.ecommerce.customer.application.service;

import com.ecommerce.customer.api.dto.request.LoginRequest;
import com.ecommerce.customer.api.dto.request.RegisterCustomerRequest;
import com.ecommerce.customer.api.dto.request.UpdateCustomerRequest;
import com.ecommerce.customer.domain.model.Customer;
import com.ecommerce.customer.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public Customer register(RegisterCustomerRequest request) {
        throw new UnsupportedOperationException();
    }

    public Customer login(LoginRequest request) {
        throw new UnsupportedOperationException();
    }

    public Customer getProfile(Long customerId) {
        throw new UnsupportedOperationException();
    }

    public Customer updateProfile(Long customerId, UpdateCustomerRequest request) {
        throw new UnsupportedOperationException();
    }
}
