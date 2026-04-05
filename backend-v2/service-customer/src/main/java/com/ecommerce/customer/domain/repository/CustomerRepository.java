package com.ecommerce.customer.domain.repository;

import com.ecommerce.customer.domain.model.Customer;
import com.ecommerce.customer.domain.model.Email;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmail(Email email);

    boolean existsByEmail(Email email);
}
