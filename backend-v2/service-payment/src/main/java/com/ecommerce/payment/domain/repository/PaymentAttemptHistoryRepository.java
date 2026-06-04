package com.ecommerce.payment.domain.repository;

import com.ecommerce.payment.domain.model.PaymentAttemptHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptHistoryRepository extends JpaRepository<PaymentAttemptHistory, Long> {
}
