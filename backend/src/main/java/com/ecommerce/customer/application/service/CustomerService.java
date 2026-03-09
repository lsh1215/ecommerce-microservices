package com.ecommerce.customer.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.customer.domain.model.Customer;
import com.ecommerce.customer.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional
    public Customer register(String email, String rawPassword, String name) {
        if (customerRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        String passwordHash = BCrypt.hashpw(rawPassword, BCrypt.gensalt());
        Customer customer = Customer.create(email, passwordHash, name);
        return customerRepository.save(customer);
    }

    public Customer login(String email, String rawPassword) {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!BCrypt.checkpw(rawPassword, customer.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return customer;
    }

    public Customer getByPublicId(String publicId) {
        return customerRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Customer", publicId));
    }
}
