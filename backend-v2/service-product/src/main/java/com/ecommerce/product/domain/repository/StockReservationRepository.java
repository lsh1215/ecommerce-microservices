package com.ecommerce.product.domain.repository;

import com.ecommerce.product.domain.model.StockReservation;
import com.ecommerce.product.domain.model.StockReservationStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    Optional<StockReservation> findByOrderIdAndVariantId(Long orderId, Long variantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE StockReservation r SET r.status = :released "
            + "WHERE r.id = :id AND r.status = :reserved")
    int markReleasedIfReserved(
            @Param("id") Long id,
            @Param("reserved") StockReservationStatus reserved,
            @Param("released") StockReservationStatus released);
}
