package com.ecommerce.order.domain.repository;

import com.ecommerce.order.domain.model.Order;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByCustomerId(Long customerId, Pageable pageable);

    Optional<Order> findByOrderNumber(String orderNumber);
}
