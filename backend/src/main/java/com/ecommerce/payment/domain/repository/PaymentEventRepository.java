package com.ecommerce.payment.domain.repository;

import com.ecommerce.payment.domain.model.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    List<PaymentEvent> findByPaymentIdOrderByCreatedAtAsc(Long paymentId);
}
