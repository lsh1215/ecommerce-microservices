package com.ecommerce.product.domain.repository;

import com.ecommerce.product.domain.model.StockUnit;
import com.ecommerce.product.domain.model.StockUnitHolder;
import com.ecommerce.product.domain.model.StockUnitStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 유닛 재고 접근.
 *
 * <p>주인을 가리키는 조건은 언제나 {@code (holder_type, holder_id, variant_id)} 셋이다.
 * 식별자만으로 좁히면 일반 예약의 주문 id와 선착순 확보의 offset이 같은 숫자일 때 서로의
 * 유닛을 확정하거나 반납한다. {@link StockUnitHolder} 참고.
 */
public interface StockUnitRepository extends JpaRepository<StockUnit, Long> {

    /**
     * AVAILABLE 유닛을 필요 개수만큼 잠근다. {@code SKIP LOCKED}로 다른 트랜잭션이 이미 잡은 row는
     * 기다리지 않고 건너뛴다 - 락 대기·경합이 없다. 반드시 READ COMMITTED 트랜잭션에서 호출한다
     * (빈/희소 상황에서 REPEATABLE READ가 잡는 gap 락과 데드락을 피하기 위해; Shopify와 동일).
     */
    @Query(value = "SELECT id FROM stock_unit WHERE variant_id = :variantId AND status = 'AVAILABLE' "
            + "ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Long> lockAvailableUnits(@Param("variantId") Long variantId, @Param("limit") int limit);

    /** 잠근 유닛을 RESERVED로 확정한다. updated_at을 DB 시계로 찍어 reaper TTL 판정이 정확하게 한다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE stock_unit SET status = 'RESERVED', holder_type = :holderType, "
            + "holder_id = :holderId, updated_at = NOW(6) WHERE id IN (:ids)", nativeQuery = true)
    int reserveUnits(@Param("ids") List<Long> ids, @Param("holderType") String holderType,
            @Param("holderId") Long holderId);

    /** 멱등성: 이 주체가 이미 확보한(RESERVED/CONFIRMED) 유닛 수. */
    long countByHolderTypeAndHolderIdAndVariantIdAndStatusIn(StockUnitHolder holderType,
            Long holderId, Long variantId, Collection<StockUnitStatus> statuses);

    long countByVariantIdAndStatus(Long variantId, StockUnitStatus status);

    /** confirm: 이 주체의 RESERVED 유닛을 CONFIRMED로(영구 소진). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE stock_unit SET status = 'CONFIRMED', updated_at = NOW(6) "
            + "WHERE holder_type = :holderType AND holder_id = :holderId "
            + "AND variant_id = :variantId AND status = 'RESERVED'", nativeQuery = true)
    int confirm(@Param("holderType") String holderType, @Param("holderId") Long holderId,
            @Param("variantId") Long variantId);

    /** release: 이 주체의 RESERVED 유닛을 AVAILABLE로 되돌린다(풀 반납). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE stock_unit SET status = 'AVAILABLE', holder_type = NULL, "
            + "holder_id = NULL, updated_at = NOW(6) "
            + "WHERE holder_type = :holderType AND holder_id = :holderId "
            + "AND variant_id = :variantId AND status = 'RESERVED'", nativeQuery = true)
    int release(@Param("holderType") String holderType, @Param("holderId") Long holderId,
            @Param("variantId") Long variantId);

    /**
     * 언더셀 방지: TTL(updated_at &lt; NOW − ttl)을 넘긴 RESERVED 유닛을 AVAILABLE로 회수한다.
     * cutoff를 DB 시계(NOW())로 계산해 커넥터 타임존 개입 없이 판정한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE stock_unit SET status = 'AVAILABLE', holder_type = NULL, "
            + "holder_id = NULL, updated_at = NOW(6) "
            + "WHERE status = 'RESERVED' AND updated_at < DATE_SUB(NOW(6), INTERVAL :ttlSeconds SECOND)",
            nativeQuery = true)
    int releaseStaleReserved(@Param("ttlSeconds") long ttlSeconds);
}
