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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE StockReservation r SET r.status = :confirmed "
            + "WHERE r.id = :id AND r.status = :reserved")
    int markConfirmedIfReserved(
            @Param("id") Long id,
            @Param("reserved") StockReservationStatus reserved,
            @Param("confirmed") StockReservationStatus confirmed);

    @Query("SELECT COALESCE(SUM(r.quantity), 0) FROM StockReservation r "
            + "WHERE r.variantId = :variantId AND r.status = :status")
    long sumQuantityByVariantIdAndStatus(
            @Param("variantId") Long variantId,
            @Param("status") StockReservationStatus status);

    /**
     * 언더셀 방지: TTL(updatedAt < cutoff)을 넘긴 RESERVED 예약을 RELEASED로 회수한다.
     * 예약 시점에 재고를 차감하지 않으므로 상태 전이만으로 드레이너 가용 용량에서 빠진다.
     */
    // TTL 비교를 DB 시계로만 한다. cutoff를 JVM LocalDateTime으로 바인딩하면 커넥터
    // (serverTimezone)의 오프셋 때문에 갓 만든 예약(드레이너의 NOW() 기록)이 만료로 오판될 수 있다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE stock_reservation SET status = :released "
            + "WHERE status = :reserved AND updated_at < DATE_SUB(NOW(6), INTERVAL :ttlSeconds SECOND)",
            nativeQuery = true)
    int releaseStaleReserved(
            @Param("reserved") String reserved,
            @Param("released") String released,
            @Param("ttlSeconds") long ttlSeconds);
}
