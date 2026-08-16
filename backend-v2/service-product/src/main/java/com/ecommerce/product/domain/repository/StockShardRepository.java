package com.ecommerce.product.domain.repository;

import com.ecommerce.product.domain.model.StockShard;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockShardRepository extends JpaRepository<StockShard, Long> {

    /**
     * 지정한 샤드에서 조건부로 차감한다.
     *
     * <p>{@code quantity >= :qty}가 UPDATE 안에 있으므로 읽고-빼고-쓰는 사이에 다른
     * 트랜잭션이 끼어들 틈이 없다. 잔량이 모자라면 0행이 갱신되고, 호출부는 그것을 실패로
     * 읽는다. 락을 명시하지 않아도 오버셀이 나지 않는 이유다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE stock_shard SET quantity = quantity - :qty, updated_at = NOW(6) "
            + "WHERE variant_id = :variantId AND shard_no = :shardNo AND quantity >= :qty",
            nativeQuery = true)
    int decrease(@Param("variantId") Long variantId,
                 @Param("shardNo") int shardNo,
                 @Param("qty") int qty);

    /** 취소·만료 시 되돌린다. 어느 샤드로 돌려도 총량은 같다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE stock_shard SET quantity = quantity + :qty, updated_at = NOW(6) "
            + "WHERE variant_id = :variantId AND shard_no = :shardNo",
            nativeQuery = true)
    int increase(@Param("variantId") Long variantId,
                 @Param("shardNo") int shardNo,
                 @Param("qty") int qty);

    List<StockShard> findByVariantId(Long variantId);

    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM StockShard s WHERE s.variantId = :variantId")
    int totalQuantity(@Param("variantId") Long variantId);

    /**
     * 잔량이 가장 많은 샤드를 잠그고 가져온다.
     *
     * <p>고른 샤드가 비었을 때 쓰는 마지막 수단이다. 총 잔량은 남았는데 특정 샤드만 0인
     * 상황에서 요청을 실패시키지 않으려면 어딘가에서 끌어와야 하고, 그 대상이 여기서
     * 정해진다. 정상 경로보다 비싸므로(정렬 + 잠금) 폴백으로만 쓴다.
     */
    @Query(value = "SELECT * FROM stock_shard WHERE variant_id = :variantId AND quantity >= :qty "
            + "ORDER BY quantity DESC LIMIT 1 FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    StockShard lockRichestShard(@Param("variantId") Long variantId, @Param("qty") int qty);
}
