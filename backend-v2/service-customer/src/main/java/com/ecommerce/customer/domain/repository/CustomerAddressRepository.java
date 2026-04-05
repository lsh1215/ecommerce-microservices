package com.ecommerce.customer.domain.repository;

import com.ecommerce.customer.domain.model.CustomerAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByCustomerId(Long customerId);

    Optional<CustomerAddress> findByCustomerIdAndIsDefaultTrue(Long customerId);
}
