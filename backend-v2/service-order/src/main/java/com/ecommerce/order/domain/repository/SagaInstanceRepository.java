package com.ecommerce.order.domain.repository;

import com.ecommerce.order.domain.model.SagaInstance;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaInstanceRepository extends JpaRepository<SagaInstance, Long> {

    Optional<SagaInstance> findByOrderNumber(String orderNumber);

    Optional<SagaInstance> findByOrderId(Long orderId);
}
