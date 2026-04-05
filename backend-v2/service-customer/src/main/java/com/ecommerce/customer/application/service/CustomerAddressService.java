package com.ecommerce.customer.application.service;

import com.ecommerce.customer.api.dto.request.CreateAddressRequest;
import com.ecommerce.customer.api.dto.request.UpdateAddressRequest;
import com.ecommerce.customer.domain.model.CustomerAddress;
import com.ecommerce.customer.domain.repository.CustomerAddressRepository;
import com.ecommerce.customer.domain.repository.CustomerRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerAddressService {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository customerAddressRepository;

    public List<CustomerAddress> getAddresses(Long customerId) {
        throw new UnsupportedOperationException();
    }

    public CustomerAddress addAddress(Long customerId, CreateAddressRequest request) {
        throw new UnsupportedOperationException();
    }

    public CustomerAddress updateAddress(Long customerId, Long addressId,
                                         UpdateAddressRequest request) {
        throw new UnsupportedOperationException();
    }

    public void deleteAddress(Long customerId, Long addressId) {
        throw new UnsupportedOperationException();
    }

    public void setDefault(Long customerId, Long addressId) {
        throw new UnsupportedOperationException();
    }
}
