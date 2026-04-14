package com.ecommerce.customer.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.customer.api.dto.request.CreateAddressRequest;
import com.ecommerce.customer.api.dto.request.UpdateAddressRequest;
import com.ecommerce.customer.api.dto.response.AddressResponse;
import com.ecommerce.customer.application.dto.CreateAddressCommand;
import com.ecommerce.customer.application.dto.UpdateAddressCommand;
import com.ecommerce.customer.application.service.CustomerAddressService;
import com.ecommerce.customer.domain.model.CustomerAddress;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers/{customerId}/addresses")
@RequiredArgsConstructor
public class CustomerAddressController {

    private final CustomerAddressService customerAddressService;

    @GetMapping
    public ApiResponse<List<AddressResponse>> getAddresses(@PathVariable Long customerId) {
        List<CustomerAddress> addresses = customerAddressService.getAddresses(customerId);
        List<AddressResponse> response = addresses.stream()
                .map(AddressResponse::from)
                .toList();
        return ApiResponse.ok(response);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AddressResponse> addAddress(@PathVariable Long customerId,
                                                   @Valid @RequestBody CreateAddressRequest request) {
        CreateAddressCommand command = new CreateAddressCommand(
                request.label(), request.recipientName(), request.phone(),
                request.zipCode(), request.address1(), request.address2(), request.isDefault()
        );
        CustomerAddress address = customerAddressService.addAddress(customerId, command);
        return ApiResponse.created(AddressResponse.from(address));
    }

    @PutMapping("/{addressId}")
    public ApiResponse<AddressResponse> updateAddress(@PathVariable Long customerId,
                                                      @PathVariable Long addressId,
                                                      @Valid @RequestBody UpdateAddressRequest request) {
        UpdateAddressCommand command = new UpdateAddressCommand(
                request.label(), request.recipientName(), request.phone(),
                request.zipCode(), request.address1(), request.address2()
        );
        CustomerAddress address = customerAddressService.updateAddress(customerId, addressId, command);
        return ApiResponse.ok(AddressResponse.from(address));
    }

    @DeleteMapping("/{addressId}")
    public ApiResponse<Void> deleteAddress(@PathVariable Long customerId,
                                           @PathVariable Long addressId) {
        customerAddressService.deleteAddress(customerId, addressId);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{addressId}/default")
    public ApiResponse<Void> setDefault(@PathVariable Long customerId,
                                        @PathVariable Long addressId) {
        customerAddressService.setDefault(customerId, addressId);
        return ApiResponse.ok(null);
    }
}
