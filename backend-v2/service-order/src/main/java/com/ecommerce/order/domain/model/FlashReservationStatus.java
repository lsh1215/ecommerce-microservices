package com.ecommerce.order.domain.model;

public enum FlashReservationStatus {
    /** 아직 granter 가 처리하지 않았다. 저장되는 상태가 아니라 조회 응답으로만 쓰인다. */
    PENDING,
    /** 유닛 확보 성공. */
    RESERVED,
    /** 재고 소진으로 미확보. 저장되지 않는다 - row 가 없고 매진이면 이 값으로 답한다. */
    SOLD_OUT
}
