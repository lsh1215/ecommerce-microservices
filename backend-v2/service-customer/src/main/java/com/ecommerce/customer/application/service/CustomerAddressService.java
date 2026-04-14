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
     * 고객의 새로운 배송지를 추가한다.
     * 기본 배송지로 지정된 경우, 기존 기본 배송지를 먼저 해제한다.
     */
    @Transactional
    public CustomerAddress addAddress(Long customerId, CreateAddressCommand command) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

        // 고객당 기본 배송지는 하나만 허용
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
     * 특정 배송지를 고객의 기본 배송지로 설정한다.
     * 기존 기본 배송지를 해제한 후 새로운 배송지를 기본으로 지정한다.
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
