package com.ecommerce.product.domain.service;

import com.ecommerce.product.domain.model.StockContention;

/**
 * 재고 차감 전략. 등급마다 직렬화 지점이 다르다.
 *
 * <p>세 구현이 같은 것을 보장한다 — <b>오버셀이 나지 않는다</b>. 다른 것은 그 보장을
 * 어디서 얻느냐다. NORMAL은 조건부 UPDATE의 {@code WHERE quantity >= ?}에서, POPULAR는
 * 샤드별 같은 조건에서, HOT은 존재하는 row 수 자체에서 얻는다.
 *
 * <p>호출부는 등급을 모른다. {@link StockReservationService}가 옵션의
 * {@link StockContention}으로 구현을 고른다.
 */
public interface StockReserver {

    /** 이 구현이 담당하는 등급. */
    StockContention contention();

    /**
     * 재고를 확보한다.
     *
     * @return 확보 성공 여부. 실패는 예외가 아니라 {@code false}다 — 재고 부족은 정상적인
     *         결과이지 오류가 아니고, 예외로 만들면 호출부가 스택트레이스 비용을 치른다.
     */
    boolean reserve(Long variantId, Long orderId, int quantity);

    /** 확보분을 되돌린다(주문 취소·예약 만료). */
    void release(Long variantId, Long orderId, int quantity);

    /** 확보분을 확정한다(결제 완료). */
    void confirm(Long variantId, Long orderId, int quantity);
}
