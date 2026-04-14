package com.ecommerce.customer.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.customer.CustomerErrorCode;
import com.ecommerce.customer.application.dto.CreateAddressCommand;
import com.ecommerce.customer.application.dto.UpdateAddressCommand;
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

    /**
     * Add a new shipping address for the customer.
     * If marked as default, unmarks the current default address first.
     */
    @Transactional
    public CustomerAddress addAddress(Long customerId, CreateAddressCommand command) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

        // Ensure only one default address per customer
        if (command.isDefault()) {
            customerAddressRepository.findByCustomerIdAndIsDefaultTrue(customerId)
                    .ifPresent(CustomerAddress::unmarkDefault);
        }

        CustomerAddress address = CustomerAddress.create(
                customer, command.label(), command.recipientName(),
                command.phone(), command.zipCode(), command.address1(), command.address2(),
                command.isDefault()
        );
        return customerAddressRepository.save(address);
    }

    @Transactional
    public CustomerAddress updateAddress(Long customerId, Long addressId, UpdateAddressCommand command) {
        CustomerAddress address = customerAddressRepository.findById(addressId)
                .filter(a -> a.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.ADDRESS_NOT_FOUND));
        address.update(command.label(), command.recipientName(), command.phone(),
                command.zipCode(), command.address1(), command.address2());
        return address;
    }

    @Transactional
    public void deleteAddress(Long customerId, Long addressId) {
        CustomerAddress address = customerAddressRepository.findById(addressId)
                .filter(a -> a.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.ADDRESS_NOT_FOUND));
        customerAddressRepository.delete(address);
    }

    /**
     * Set an address as the customer's default.
     * Unmarks the previous default before marking the new one.
     */
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
