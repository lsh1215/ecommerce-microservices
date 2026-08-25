package com.ecommerce.product.domain.model;

/**
 * 유닛을 쥔 주체의 종류.
 *
 * <p>유닛의 주인은 {@code (holderType, holderId)} 짝으로 정해진다. 식별자만으로는 정해지지
 * 않는다. 일반 예약의 {@code holderId}는 주문 id이고 선착순 확보의 {@code holderId}는 접수
 * 메시지의 Kafka offset인데, 두 값은 서로 다른 번호 공간에서 나오므로 같은 상품에서 같은
 * 숫자가 나올 수 있다. 종류를 빼고 식별자만 비교하면 다른 주체의 유닛을 확정하거나
 * 반납하게 된다.
 */
public enum StockUnitHolder {
    /** 일반 예약. 식별자는 주문 id. */
    ORDER,
    /** 선착순 확보. 식별자는 접수 메시지의 파티션 offset. */
    FLASH
}
