package com.ecommerce.order.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 유닛 확보에 성공한 예약. <b>승자만</b> 남는다.
 *
 * <p>접수 시점에는 아무것도 쓰지 않는다. granter 가 확보에 성공한 뒤에야 이 row 가 생긴다.
 * 탈락자까지 기록하면 그 수가 재고와 무관하게 늘어나, DB 쓰기를 줄이려던 설계가 무의미해진다.
 * 탈락은 "row 가 없고 그 상품이 매진"으로 판정한다.
 *
 * <p>공정 순번은 이 row 의 id 가 아니라 Kafka 파티션의 offset 이다. id 는 확보된 순서일 뿐이다.
 */
@Entity
@Table(name = "flash_reservation",
        uniqueConstraints = @UniqueConstraint(name = "uk_flash_reservation_record",
                columnNames = {"partition_no", "record_offset"}),
        indexes = {@Index(name = "ix_flash_reservation_variant", columnList = "variant_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlashReservation extends BaseEntity {

    /**
     * 접수 메시지의 파티션과 offset. 이 둘이 식별자이자 공정 순번이다.
     *
     * <p>{@code partition}/{@code offset}은 MySQL 예약어라 컬럼명을 바꿔 둔다.
     */
    @Column(name = "partition_no", nullable = false)
    private int partitionNo;

    @Column(name = "record_offset", nullable = false)
    private long recordOffset;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlashReservationStatus status;

    public static FlashReservation granted(int partitionNo, long recordOffset, Long customerId,
                                           Long variantId, int quantity) {
        FlashReservation reservation = new FlashReservation();
        reservation.partitionNo = partitionNo;
        reservation.recordOffset = recordOffset;
        reservation.customerId = customerId;
        reservation.variantId = variantId;
        reservation.quantity = quantity;
        reservation.status = FlashReservationStatus.RESERVED;
        return reservation;
    }
}
