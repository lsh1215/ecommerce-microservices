package com.ecommerce.order.domain.repository;

import com.ecommerce.order.domain.model.Orders;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Orders, Long> {

    @Query("SELECT o FROM Orders o LEFT JOIN FETCH o.items WHERE o.publicId = :publicId")
    Optional<Orders> findByPublicId(@Param("publicId") String publicId);

    Page<Orders> findByCustomerId(Long customerId, Pageable pageable);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
