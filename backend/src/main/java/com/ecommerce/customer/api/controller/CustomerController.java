package com.ecommerce.customer.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.customer.api.dto.request.CreateAddressRequest;
import com.ecommerce.customer.api.dto.request.CreateCustomerRequest;
import com.ecommerce.customer.api.dto.request.LoginRequest;
import com.ecommerce.customer.api.dto.request.UpdateAddressRequest;
import com.ecommerce.customer.api.dto.response.AddressResponse;
import com.ecommerce.customer.api.dto.response.CustomerResponse;
import com.ecommerce.customer.api.dto.response.LoginResponse;
import com.ecommerce.customer.application.service.CustomerAddressService;
import com.ecommerce.customer.application.service.CustomerService;
import com.ecommerce.customer.domain.model.Customer;
import com.ecommerce.customer.domain.model.CustomerAddress;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerAddressService addressService;

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> register(@Valid @RequestBody CreateCustomerRequest request) {
        Customer customer = customerService.register(request.email(), request.password(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(CustomerResponse.from(customer)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        Customer customer = customerService.login(request.email(), request.password());
        return ResponseEntity.ok(ApiResponse.success(LoginResponse.from(customer)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getById(@PathVariable String id) {
        Customer customer = customerService.getByPublicId(id);
        return ResponseEntity.ok(ApiResponse.success(CustomerResponse.from(customer)));
    }

    @PostMapping("/{customerId}/addresses")
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(
            @PathVariable String customerId,
            @Valid @RequestBody CreateAddressRequest request) {
        CustomerAddress address = addressService.addAddress(customerId, request.label(),
                request.recipientName(), request.phone(), request.street(), request.detail(),
                request.city(), request.stateProvince(), request.postalCode(), request.country(),
                request.isDefault());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(AddressResponse.from(address)));
    }

    @GetMapping("/{customerId}/addresses")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> listAddresses(@PathVariable String customerId) {
        List<AddressResponse> addresses = addressService.getAddresses(customerId).stream()
                .map(AddressResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(addresses));
    }

    @PutMapping("/{customerId}/addresses/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @PathVariable String customerId,
            @PathVariable String addressId,
            @Valid @RequestBody UpdateAddressRequest request) {
        CustomerAddress address = addressService.updateAddress(customerId, addressId, request.label(),
                request.recipientName(), request.phone(), request.street(), request.detail(),
                request.city(), request.stateProvince(), request.postalCode(), request.country(),
                request.isDefault());
        return ResponseEntity.ok(ApiResponse.success(AddressResponse.from(address)));
    }

    @DeleteMapping("/{customerId}/addresses/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @PathVariable String customerId,
            @PathVariable String addressId) {
        addressService.deleteAddress(customerId, addressId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
