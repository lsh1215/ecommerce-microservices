package com.ecommerce.product.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Shopify식 재고 예약 단위 — 재고 1개당 row 1줄(수량 컬럼 하나가 아니라).
 *
 * <p>예약은 {@code SELECT ... FOR UPDATE SKIP LOCKED}로 AVAILABLE 유닛을 그 자리에서 필요한 개수만큼
 * 집어 RESERVED로 바꾼다(동기). 서로 다른 row라 재고 row 락 경합이 없고, 존재하는 row 수가 곧 재고
 * 상한이므로 <b>오버셀이 구조적으로 불가능</b>하다(있는 row보다 더 집을 수 없다). 큐·드레이너·폴링·
 * 인메모리 캐시가 필요 없다.
 */
@Entity
@Table(name = "stock_unit", indexes = {
        @Index(name = "ix_stock_unit_variant_status", columnList = "variant_id, status, id"),
        @Index(name = "ix_stock_unit_order", columnList = "order_id, variant_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockUnit extends BaseEntity {

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    /** RESERVED/CONFIRMED일 때 그 유닛을 쥔 주문. AVAILABLE이면 null. */
    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockUnitStatus status;

    public static StockUnit available(Long variantId) {
        StockUnit unit = new StockUnit();
        unit.variantId = variantId;
        unit.status = StockUnitStatus.AVAILABLE;
        return unit;
    }
}
