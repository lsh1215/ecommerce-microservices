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
     * 새로운 고객 계정을 등록한다.
     * 저장 전 이메일 중복 여부를 검증한다.
     */
    @Transactional
    public Customer register(RegisterCustomerCommand command) {
        Email email = new Email(command.email());
        // 가드: 이메일 중복 등록 방지
        if (customerRepository.existsByEmail(email)) {
            throw new BusinessException(CustomerErrorCode.DUPLICATE_EMAIL);
        }
        Customer customer = Customer.create(email, command.password(), command.name(), command.phone());
        return customerRepository.save(customer);
    }

    /**
     * 이메일과 비밀번호로 고객을 인증한다.
     * 성공 시 Customer 엔티티를 반환하고, 자격 증명이 유효하지 않으면 예외를 발생시킨다.
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
