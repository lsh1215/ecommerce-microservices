package com.ecommerce.customer.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.customer.CustomerErrorCode;
import com.ecommerce.customer.api.dto.request.CreateAddressRequest;
import com.ecommerce.customer.api.dto.request.UpdateAddressRequest;
import com.ecommerce.customer.domain.model.Customer;
import com.ecommerce.customer.domain.model.CustomerAddress;
import com.ecommerce.customer.domain.repository.CustomerAddressRepository;
import com.ecommerce.customer.domain.repository.CustomerRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerAddressService {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository customerAddressRepository;

    public List<CustomerAddress> getAddresses(Long customerId) {
        return customerAddressRepository.findByCustomerId(customerId);
    }

    @Transactional
    public CustomerAddress addAddress(Long customerId, CreateAddressRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

        if (request.isDefault()) {
            customerAddressRepository.findByCustomerIdAndIsDefaultTrue(customerId)
                    .ifPresent(CustomerAddress::unmarkDefault);
        }

        CustomerAddress address = CustomerAddress.create(
                customer, request.label(), request.recipientName(),
                request.phone(), request.zipCode(), request.address1(), request.address2(),
                request.isDefault()
        );
        return customerAddressRepository.save(address);
    }

    @Transactional
    public CustomerAddress updateAddress(Long customerId, Long addressId, UpdateAddressRequest request) {
        CustomerAddress address = customerAddressRepository.findById(addressId)
                .filter(a -> a.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.ADDRESS_NOT_FOUND));
        address.update(request.label(), request.recipientName(), request.phone(),
                request.zipCode(), request.address1(), request.address2());
        return address;
    }

    @Transactional
    public void deleteAddress(Long customerId, Long addressId) {
        CustomerAddress address = customerAddressRepository.findById(addressId)
                .filter(a -> a.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.ADDRESS_NOT_FOUND));
        customerAddressRepository.delete(address);
    }

    @Transactional
    public void setDefault(Long customerId, Long addressId) {
        CustomerAddress address = customerAddressRepository.findById(addressId)
                .filter(a -> a.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.ADDRESS_NOT_FOUND));
        customerAddressRepository.findByCustomerIdAndIsDefaultTrue(customerId)
                .ifPresent(CustomerAddress::unmarkDefault);
        address.markAsDefault();
    }
}
