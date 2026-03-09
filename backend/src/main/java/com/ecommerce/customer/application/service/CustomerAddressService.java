package com.ecommerce.customer.application.service;

import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.customer.domain.model.Customer;
import com.ecommerce.customer.domain.model.CustomerAddress;
import com.ecommerce.customer.domain.repository.CustomerAddressRepository;
import com.ecommerce.customer.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerAddressService {

    private final CustomerAddressRepository addressRepository;
    private final CustomerRepository customerRepository;

    @Transactional
    public CustomerAddress addAddress(String customerPublicId, String label, String recipientName,
                                       String phone, String street, String detail, String city,
                                       String stateProvince, String postalCode, String country,
                                       boolean isDefault) {
        Customer customer = findCustomerByPublicId(customerPublicId);
        if (isDefault) {
            clearCurrentDefault(customer.getId());
        }
        CustomerAddress address = CustomerAddress.create(customer, label, recipientName, phone,
                street, detail, city, stateProvince, postalCode, country, isDefault);
        return addressRepository.save(address);
    }

    public List<CustomerAddress> getAddresses(String customerPublicId) {
        Customer customer = findCustomerByPublicId(customerPublicId);
        return addressRepository.findByCustomerId(customer.getId());
    }

    @Transactional
    public CustomerAddress updateAddress(String customerPublicId, String addressPublicId,
                                          String label, String recipientName, String phone,
                                          String street, String detail, String city,
                                          String stateProvince, String postalCode, String country,
                                          boolean isDefault) {
        Customer customer = findCustomerByPublicId(customerPublicId);
        CustomerAddress address = findAddressByPublicId(addressPublicId);
        if (isDefault) {
            clearCurrentDefault(customer.getId());
        }
        address.update(label, recipientName, phone, street, detail, city,
                stateProvince, postalCode, country, isDefault);
        return addressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(String customerPublicId, String addressPublicId) {
        findCustomerByPublicId(customerPublicId);
        CustomerAddress address = findAddressByPublicId(addressPublicId);
        addressRepository.delete(address);
    }

    private Customer findCustomerByPublicId(String publicId) {
        return customerRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Customer", publicId));
    }

    private CustomerAddress findAddressByPublicId(String publicId) {
        return addressRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("CustomerAddress", publicId));
    }

    private void clearCurrentDefault(Long customerId) {
        addressRepository.findByCustomerIdAndIsDefaultTrue(customerId)
                .ifPresent(CustomerAddress::clearDefault);
    }
}
