package com.ecommerce.order.domain.repository;

import com.ecommerce.order.domain.model.FlashReservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlashReservationRepository extends JpaRepository<FlashReservation, Long> {

    java.util.Optional<FlashReservation> findByPartitionNoAndRecordOffset(int partitionNo, long recordOffset);
}
