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
 * {@link StockContention#HOT} 등급 옵션의 재고 조각 — 재고 1개당 row 1줄.
 *
 * <p>확보는 {@code SELECT ... FOR UPDATE SKIP LOCKED}로 AVAILABLE 유닛을 그 자리에서 집어
 * RESERVED로 바꾼다. 요청마다 서로 다른 row를 잡으므로 경합이 사실상 사라지고, 존재하는
 * row 수가 곧 재고 상한이라 <b>오버셀이 구조적으로 불가능</b>하다.
 *
 * <p><b>SKIP LOCKED는 선택이 아니다.</b> 이걸 빼면 동시 요청이 전부 "가장 앞선 AVAILABLE
 * row" 하나를 잠그려 하고 나머지는 거기서 블로킹된다. row를 100만 개로 쪼개도 경합이 그
 * 한 row로 다시 모이므로, 유닛 방식은 SKIP LOCKED와 짝일 때만 의미가 있다.
 *
 * <p><b>언제 쓰나</b>: 요청당 DB 작업이 세 등급 중 가장 많다(잠금 스캔 + row UPDATE +
 * 보조 인덱스 2개 갱신). 단일 옵션에 트래픽이 몰려 {@link StockContention#NORMAL}의 단일
 * row나 {@link StockContention#POPULAR}의 N샤드로도 직렬화가 풀리지 않을 때만 값을 한다.
 * 좌석·시리얼처럼 재고가 개별 식별되는 도메인이라면 등급과 무관하게 이 모델이 맞다.
 */
@Entity
@Table(name = "stock_unit", indexes = {
        @Index(name = "ix_stock_unit_variant_status", columnList = "variant_id, status, id"),
        @Index(name = "ix_stock_unit_order", columnList = "order_id, variant_id"),
        // TTL 회수용. 이 인덱스가 없으면 리퍼의 WHERE status='RESERVED' AND
        // updated_at < ... 가 선행 컬럼이 variant_id인 인덱스를 못 타고 PRIMARY
        // 전체를 훑는다. 5초마다 도는 작업이라 유닛 100만 기준으로 상시 부하가
        // 됐고, 측정에서는 예약 지연이 런마다 흔들리는 원인이었다.
        @Index(name = "ix_stock_unit_status_updated", columnList = "status, updated_at")
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
