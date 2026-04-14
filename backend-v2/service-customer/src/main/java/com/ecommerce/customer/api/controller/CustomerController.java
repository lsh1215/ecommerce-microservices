package com.ecommerce.customer.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.customer.api.dto.request.LoginRequest;
import com.ecommerce.customer.api.dto.request.RegisterCustomerRequest;
import com.ecommerce.customer.api.dto.request.UpdateCustomerRequest;
import com.ecommerce.customer.api.dto.response.CustomerResponse;
import com.ecommerce.customer.api.dto.response.LoginResponse;
import com.ecommerce.customer.application.dto.LoginCommand;
import com.ecommerce.customer.application.dto.RegisterCustomerCommand;
import com.ecommerce.customer.application.dto.UpdateCustomerCommand;
import com.ecommerce.customer.application.service.CustomerService;
import com.ecommerce.customer.domain.model.Customer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CustomerResponse> register(@Valid @RequestBody RegisterCustomerRequest request) {
        RegisterCustomerCommand command = new RegisterCustomerCommand(
                request.email(), request.password(), request.name(), request.phone()
        );
        Customer customer = customerService.register(command);
        return ApiResponse.created(CustomerResponse.from(customer));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginCommand command = new LoginCommand(request.email(), request.password());
        Customer customer = customerService.login(command);
        return ApiResponse.ok(LoginResponse.from(customer));
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> getProfile(@PathVariable Long id) {
        Customer customer = customerService.getProfile(id);
        return ApiResponse.ok(CustomerResponse.from(customer));
    }

    @PutMapping("/{id}")
    public ApiResponse<CustomerResponse> updateProfile(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateCustomerRequest request) {
        UpdateCustomerCommand command = new UpdateCustomerCommand(request.name(), request.phone());
        Customer customer = customerService.updateProfile(id, command);
        return ApiResponse.ok(CustomerResponse.from(customer));
    }
}
