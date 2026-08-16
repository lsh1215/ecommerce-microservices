package com.ecommerce.product.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@link StockContention#POPULAR} 등급 옵션의 재고 조각.
 *
 * <p>재고 500개를 row 하나에 두면 동시 주문이 그 row 하나에서 직렬화된다. 같은 500개를
 * 샤드 16개에 나눠 두고 주문마다 임의 샤드를 깎으면 경합이 1/16이 된다. 재고 1개당 row를
 * 만드는 {@link StockUnit}과 달리 row 수가 재고량이 아니라 샤드 수에 비례하므로, 재고가
 * 많아도 테이블이 커지지 않는다.
 *
 * <p><b>대가</b>: 총 잔량이 남아 있어도 방금 고른 샤드가 0이면 그 요청은 실패한다. 잔량이
 * 적어질수록 빈 샤드를 고를 확률이 올라가므로, 마지막 재고를 소진하려면 다른 샤드에서
 * 끌어오는 재조정이 필요하다. 이 비용을 감수할 만한 구간이 "단일 row로는 막히지만 재고
 * 1개당 row까지는 과한" 중간 경합이다.
 */
@Entity
@Table(
        name = "stock_shard",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_shard_variant_no",
                columnNames = {"variant_id", "shard_no"}),
        // 차감은 (variant_id, shard_no)로 단건을 찍고, 재조정은 variant 단위로 잔량을
        // 훑는다. 둘 다 이 인덱스로 처리된다.
        indexes = @Index(name = "ix_stock_shard_variant", columnList = "variant_id, shard_no"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockShard extends BaseEntity {

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "shard_no", nullable = false)
    private int shardNo;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    public static StockShard of(Long variantId, int shardNo, int quantity) {
        StockShard shard = new StockShard();
        shard.variantId = variantId;
        shard.shardNo = shardNo;
        shard.quantity = quantity;
        return shard;
    }
}
