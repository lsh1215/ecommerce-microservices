package com.ecommerce.customer.application.service;

import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.customer.domain.model.Customer;
import com.ecommerce.customer.domain.model.CustomerAddress;
import com.ecommerce.customer.domain.repository.CustomerAddressRepository;
import com.ecommerce.customer.domain.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomerAddressServiceTest {

    @Mock
    private CustomerAddressRepository addressRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerAddressService addressService;

    private Customer createCustomer() {
        return Customer.create("test@example.com", "hash", "Test User");
    }

    @Test
    void addAddress_shouldSaveAndReturnAddress() {
        Customer customer = createCustomer();
        given(customerRepository.findByPublicId("CUST_ULID")).willReturn(Optional.of(customer));
        given(addressRepository.save(any(CustomerAddress.class))).willAnswer(inv -> inv.getArgument(0));

        CustomerAddress result = addressService.addAddress("CUST_ULID", "Home", "John",
                "010-1234-5678", "123 Main St", "Apt 4B", "Seoul", "Seoul",
                "12345", "KR", false);

        assertThat(result.getRecipientName()).isEqualTo("John");
        assertThat(result.isDefault()).isFalse();
        verify(addressRepository).save(any(CustomerAddress.class));
    }

    @Test
    void addAddress_shouldClearPreviousDefaultWhenNewIsDefault() {
        Customer customer = createCustomer();
        CustomerAddress existingDefault = CustomerAddress.create(customer, "Old", "Old",
                "010-0000-0000", "Old St", null, "Seoul", null, "00000", "KR", true);

        given(customerRepository.findByPublicId("CUST_ULID")).willReturn(Optional.of(customer));
        given(addressRepository.findByCustomerIdAndIsDefaultTrue(customer.getId()))
                .willReturn(Optional.of(existingDefault));
        given(addressRepository.save(any(CustomerAddress.class))).willAnswer(inv -> inv.getArgument(0));

        CustomerAddress result = addressService.addAddress("CUST_ULID", "Home", "John",
                "010-1234-5678", "123 Main St", null, "Seoul", null,
                "12345", "KR", true);

        assertThat(result.isDefault()).isTrue();
        assertThat(existingDefault.isDefault()).isFalse();
    }

    @Test
    void addAddress_shouldThrowWhenCustomerNotFound() {
        given(customerRepository.findByPublicId("NONEXISTENT")).willReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.addAddress("NONEXISTENT", "Home", "John",
                "010-1234-5678", "Street", null, "City", null, "12345", "KR", false))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getAddresses_shouldReturnListForCustomer() {
        Customer customer = createCustomer();
        given(customerRepository.findByPublicId("CUST_ULID")).willReturn(Optional.of(customer));
        given(addressRepository.findByCustomerId(customer.getId())).willReturn(List.of(
                CustomerAddress.create(customer, "Home", "John", "010-1111-1111",
                        "Street 1", null, "Seoul", null, "11111", "KR", true)
        ));

        List<CustomerAddress> result = addressService.getAddresses("CUST_ULID");

        assertThat(result).hasSize(1);
    }

    @Test
    void updateAddress_shouldUpdateFields() {
        Customer customer = createCustomer();
        CustomerAddress address = CustomerAddress.create(customer, "Home", "John",
                "010-1111-1111", "Old St", null, "Seoul", null, "11111", "KR", false);

        given(customerRepository.findByPublicId("CUST_ULID")).willReturn(Optional.of(customer));
        given(addressRepository.findByPublicId("ADDR_ULID")).willReturn(Optional.of(address));
        given(addressRepository.save(any(CustomerAddress.class))).willAnswer(inv -> inv.getArgument(0));

        CustomerAddress result = addressService.updateAddress("CUST_ULID", "ADDR_ULID",
                "Office", "Jane", "010-2222-2222", "New St", "Suite 5",
                "Busan", "Busan", "22222", "KR", false);

        assertThat(result.getLabel()).isEqualTo("Office");
        assertThat(result.getRecipientName()).isEqualTo("Jane");
        assertThat(result.getCity()).isEqualTo("Busan");
    }

    @Test
    void updateAddress_shouldClearPreviousDefaultWhenSettingNewDefault() {
        Customer customer = createCustomer();
        CustomerAddress existingDefault = CustomerAddress.create(customer, "Old Default", "Old",
                "010-0000-0000", "Old St", null, "Seoul", null, "00000", "KR", true);
        CustomerAddress address = CustomerAddress.create(customer, "Home", "John",
                "010-1111-1111", "Street", null, "Seoul", null, "11111", "KR", false);

        given(customerRepository.findByPublicId("CUST_ULID")).willReturn(Optional.of(customer));
        given(addressRepository.findByPublicId("ADDR_ULID")).willReturn(Optional.of(address));
        given(addressRepository.findByCustomerIdAndIsDefaultTrue(customer.getId()))
                .willReturn(Optional.of(existingDefault));
        given(addressRepository.save(any(CustomerAddress.class))).willAnswer(inv -> inv.getArgument(0));

        CustomerAddress result = addressService.updateAddress("CUST_ULID", "ADDR_ULID",
                "Home", "John", "010-1111-1111", "Street", null,
                "Seoul", null, "11111", "KR", true);

        assertThat(result.isDefault()).isTrue();
        assertThat(existingDefault.isDefault()).isFalse();
    }

    @Test
    void updateAddress_shouldThrowWhenAddressNotFound() {
        Customer customer = createCustomer();
        given(customerRepository.findByPublicId("CUST_ULID")).willReturn(Optional.of(customer));
        given(addressRepository.findByPublicId("NONEXISTENT")).willReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.updateAddress("CUST_ULID", "NONEXISTENT",
                "Home", "John", "010-1111-1111", "Street", null,
                "Seoul", null, "11111", "KR", false))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void deleteAddress_shouldRemoveAddress() {
        Customer customer = createCustomer();
        CustomerAddress address = CustomerAddress.create(customer, "Home", "John",
                "010-1111-1111", "Street", null, "Seoul", null, "11111", "KR", false);

        given(customerRepository.findByPublicId("CUST_ULID")).willReturn(Optional.of(customer));
        given(addressRepository.findByPublicId("ADDR_ULID")).willReturn(Optional.of(address));

        addressService.deleteAddress("CUST_ULID", "ADDR_ULID");

        verify(addressRepository).delete(address);
    }

    @Test
    void deleteAddress_shouldThrowWhenAddressNotFound() {
        Customer customer = createCustomer();
        given(customerRepository.findByPublicId("CUST_ULID")).willReturn(Optional.of(customer));
        given(addressRepository.findByPublicId("NONEXISTENT")).willReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.deleteAddress("CUST_ULID", "NONEXISTENT"))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
