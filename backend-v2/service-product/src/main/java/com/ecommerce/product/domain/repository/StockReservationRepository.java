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
     * 같은 주문이 같은 옵션을 이미 잡았는지.
     *
     * <p>재시도나 중복 전송으로 예약이 두 번 들어오면 재고가 이중으로 깎인다. 엔티티를
     * 가져올 필요는 없고 존재 여부만 필요하므로 exists로 확인한다.
     */
    boolean existsByOrderIdAndVariantId(Long orderId, Long variantId);

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE StockReservation r SET r.status = :confirmed "
            + "WHERE r.id = :id AND r.status = :reserved")
    int markConfirmedIfReserved(
            @Param("id") Long id,
            @Param("reserved") StockReservationStatus reserved,
            @Param("confirmed") StockReservationStatus confirmed);
}
