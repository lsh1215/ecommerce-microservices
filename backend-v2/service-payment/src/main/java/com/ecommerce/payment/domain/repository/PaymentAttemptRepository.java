package com.ecommerce.payment.domain.repository;

import com.ecommerce.payment.domain.model.PaymentAttempt;
import com.ecommerce.payment.domain.model.PaymentAttemptStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentAttempt> findFirstByStatusInOrderByRequestedAtAsc(Collection<PaymentAttemptStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentAttempt> findFirstByOrderIdAndStatusInOrderByRequestedAtDesc(
            Long orderId, Collection<PaymentAttemptStatus> statuses);

    Optional<PaymentAttempt> findFirstByOrderIdOrderByRequestedAtDesc(Long orderId);
}
