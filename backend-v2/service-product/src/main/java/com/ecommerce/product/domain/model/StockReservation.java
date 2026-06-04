package com.ecommerce.product.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "stock_reservation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_reservation_order_variant",
                columnNames = {"order_id", "variant_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReservation extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockReservationStatus status;

    /**
     * orderId와 variantId 조합을 재고 예약의 비즈니스 키로 사용한다.
     * 같은 주문의 같은 옵션 예약은 하나의 예약 이력으로 수렴해야 한다.
     */
    public static StockReservation reserve(Long orderId, Long variantId, int quantity) {
        StockReservation reservation = new StockReservation();
        reservation.orderId = orderId;
        reservation.variantId = variantId;
        reservation.quantity = quantity;
        reservation.status = StockReservationStatus.RESERVED;
        return reservation;
    }

    public boolean isReleased() {
        return status == StockReservationStatus.RELEASED;
    }

    public boolean isConfirmed() {
        return status == StockReservationStatus.CONFIRMED;
    }

}
