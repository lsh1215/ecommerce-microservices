package com.ecommerce.customer.domain.repository;

import com.ecommerce.customer.domain.model.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByCustomerId(Long customerId);

    Optional<CustomerAddress> findByPublicId(String publicId);

    Optional<CustomerAddress> findByCustomerIdAndIsDefaultTrue(Long customerId);
}
