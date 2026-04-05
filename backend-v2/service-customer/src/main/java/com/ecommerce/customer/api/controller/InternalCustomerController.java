package com.ecommerce.customer.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.customer.api.dto.response.CustomerResponse;
import com.ecommerce.customer.application.service.CustomerService;
import com.ecommerce.customer.domain.model.Customer;
import com.ecommerce.customer.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/customers")
@RequiredArgsConstructor
public class InternalCustomerController {

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;

    @GetMapping("/{id}/exists")
    public ApiResponse<Boolean> exists(@PathVariable Long id) {
        boolean exists = customerRepository.existsById(id);
        return ApiResponse.ok(exists);
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> getCustomer(@PathVariable Long id) {
        Customer customer = customerService.getProfile(id);
        return ApiResponse.ok(CustomerResponse.from(customer));
    }
}
