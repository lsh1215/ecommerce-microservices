package com.ecommerce.customer.domain.repository;

import com.ecommerce.common.config.JpaAuditingConfig;
import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.customer.domain.model.Customer;
import com.ecommerce.customer.domain.model.CustomerAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({TestContainersConfig.class, JpaAuditingConfig.class})
class CustomerAddressRepositoryTest {

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainersConfig.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainersConfig.MYSQL::getUsername);
        registry.add("spring.datasource.password", TestContainersConfig.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private CustomerAddressRepository addressRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private Customer customer;

    @BeforeEach
    void setUp() {
        addressRepository.deleteAll();
        customerRepository.deleteAll();
        customer = customerRepository.save(Customer.create("addr@example.com", "hash", "Addr User"));
    }

    @Test
    void save_shouldPersistAddressWithGeneratedPublicId() {
        CustomerAddress address = CustomerAddress.create(customer, "Home", "John Doe",
                "010-1234-5678", "123 Main St", "Apt 4B", "Seoul", "Seoul",
                "12345", "KR", true);

        CustomerAddress saved = addressRepository.save(address);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPublicId()).isNotNull().hasSize(26);
        assertThat(saved.getRecipientName()).isEqualTo("John Doe");
        assertThat(saved.isDefault()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findByCustomerId_shouldReturnAllAddressesForCustomer() {
        addressRepository.save(CustomerAddress.create(customer, "Home", "John", "010-1111-1111",
                "Street 1", null, "Seoul", null, "11111", "KR", true));
        addressRepository.save(CustomerAddress.create(customer, "Office", "John", "010-2222-2222",
                "Street 2", null, "Busan", null, "22222", "KR", false));

        List<CustomerAddress> addresses = addressRepository.findByCustomerId(customer.getId());

        assertThat(addresses).hasSize(2);
    }

    @Test
    void findByPublicId_shouldReturnAddressWhenExists() {
        CustomerAddress saved = addressRepository.save(CustomerAddress.create(customer, "Home", "John",
                "010-1111-1111", "Street 1", null, "Seoul", null, "11111", "KR", false));

        Optional<CustomerAddress> found = addressRepository.findByPublicId(saved.getPublicId());

        assertThat(found).isPresent();
        assertThat(found.get().getLabel()).isEqualTo("Home");
    }

    @Test
    void findByCustomerIdAndIsDefaultTrue_shouldReturnDefaultAddress() {
        addressRepository.save(CustomerAddress.create(customer, "Home", "John", "010-1111-1111",
                "Street 1", null, "Seoul", null, "11111", "KR", true));
        addressRepository.save(CustomerAddress.create(customer, "Office", "John", "010-2222-2222",
                "Street 2", null, "Busan", null, "22222", "KR", false));

        Optional<CustomerAddress> defaultAddr = addressRepository.findByCustomerIdAndIsDefaultTrue(customer.getId());

        assertThat(defaultAddr).isPresent();
        assertThat(defaultAddr.get().getLabel()).isEqualTo("Home");
    }
}
