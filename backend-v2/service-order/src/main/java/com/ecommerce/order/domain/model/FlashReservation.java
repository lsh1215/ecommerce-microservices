package com.ecommerce.order.domain.model;

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
 * 선착순 예약 접수 레코드 — 사용자가 "구매"를 누른 그 순간 Order DB에 durable하게 남는 티켓.
 *
 * <p>{@code id}(auto-increment)가 공정 기준점(접수 순번)이다. 접수와 동시에 outbox로
 * {@code flash.reserve.requested} 를 발행하고, granter 결과({@code flash.reserve.result})가
 * 돌아오면 RESERVED/SOLD_OUT 로 확정된다. 접수는 Order DB만 건드리므로(각자 다른 row = 무경합)
 * Kafka가 잠깐 흔들려도 사용자 경로는 막히지 않는다.
 */
@Entity
@Table(name = "flash_reservation", indexes = {
        @Index(name = "ix_flash_reservation_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlashReservation extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlashReservationStatus status;

    public static FlashReservation pending(Long customerId, Long variantId, int quantity) {
        FlashReservation reservation = new FlashReservation();
        reservation.customerId = customerId;
        reservation.variantId = variantId;
        reservation.quantity = quantity;
        reservation.status = FlashReservationStatus.PENDING;
        return reservation;
    }

    /** 결과 적용은 PENDING 에서 한 번만 — 재전송/중복 결과에도 멱등. 적용됐으면 true. */
    public boolean applyResult(FlashReservationStatus result) {
        if (this.status != FlashReservationStatus.PENDING) {
            return false;
        }
        this.status = result;
        return true;
    }
}
