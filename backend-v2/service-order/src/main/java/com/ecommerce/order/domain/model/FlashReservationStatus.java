package com.ecommerce.order.domain.model;

public enum FlashReservationStatus {
    /** 접수됨(공정 순번 확정). granter 처리 대기. */
    PENDING,
    /** 유닛 확보 성공. */
    RESERVED,
    /** 재고 소진으로 미확보. */
    SOLD_OUT
}
