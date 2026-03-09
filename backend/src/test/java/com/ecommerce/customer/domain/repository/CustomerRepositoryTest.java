package com.ecommerce.customer.domain.repository;

import com.ecommerce.common.config.JpaAuditingConfig;
import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.customer.domain.model.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({TestContainersConfig.class, JpaAuditingConfig.class})
class CustomerRepositoryTest {

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainersConfig.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainersConfig.MYSQL::getUsername);
        registry.add("spring.datasource.password", TestContainersConfig.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
    }

    @Test
    void save_shouldPersistCustomerWithGeneratedPublicId() {
        Customer customer = Customer.create("test@example.com", "hashedpw", "John Doe");

        Customer saved = customerRepository.save(customer);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPublicId()).isNotNull();
        assertThat(saved.getPublicId()).hasSize(26);
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
        assertThat(saved.getName()).isEqualTo("John Doe");
        assertThat(saved.getRole()).isEqualTo("CUSTOMER");
        assertThat(saved.getPreferredCurrency()).isEqualTo("USD");
        assertThat(saved.getPreferredLocale()).isEqualTo("en");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByEmail_shouldReturnCustomerWhenExists() {
        Customer customer = Customer.create("find@example.com", "hashedpw", "Jane Doe");
        customerRepository.save(customer);

        Optional<Customer> found = customerRepository.findByEmail("find@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("find@example.com");
    }

    @Test
    void findByEmail_shouldReturnEmptyWhenNotExists() {
        Optional<Customer> found = customerRepository.findByEmail("nonexistent@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    void existsByEmail_shouldReturnTrueWhenExists() {
        Customer customer = Customer.create("exists@example.com", "hashedpw", "Bob");
        customerRepository.save(customer);

        assertThat(customerRepository.existsByEmail("exists@example.com")).isTrue();
    }

    @Test
    void existsByEmail_shouldReturnFalseWhenNotExists() {
        assertThat(customerRepository.existsByEmail("nope@example.com")).isFalse();
    }

    @Test
    void findByPublicId_shouldReturnCustomerWhenExists() {
        Customer customer = Customer.create("pub@example.com", "hashedpw", "Alice");
        Customer saved = customerRepository.save(customer);

        Optional<Customer> found = customerRepository.findByPublicId(saved.getPublicId());

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("pub@example.com");
    }
}
