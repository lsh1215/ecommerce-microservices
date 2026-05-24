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

    /**
     * 보상 트랜잭션 중복 실행을 막기 위한 상태 전이 쿼리.
     *
     * <p>RESERVED 상태인 row 하나만 RELEASED로 바꾼 호출이 재고 복구 권한을 가진다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE StockReservation r SET r.status = :released "
            + "WHERE r.id = :id AND r.status = :reserved")
    int markReleasedIfReserved(
            @Param("id") Long id,
            @Param("reserved") StockReservationStatus reserved,
            @Param("released") StockReservationStatus released);
}
