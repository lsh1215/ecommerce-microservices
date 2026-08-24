package com.ecommerce.common.config;

public final class KafkaTopics {

    private KafkaTopics() {
    }

    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_CANCELLED = "order.cancelled";

    public static final String PAYMENT_REQUESTED = "payment.requested";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";

    public static final String PRODUCT_STOCK_RESERVED = "product.stock-reserved";
    public static final String PRODUCT_STOCK_RELEASED = "product.stock-released";
    public static final String STOCK_RESERVATION_CONFIRM_REQUESTED = "stock.reservation.confirm.requested";
    public static final String STOCK_RESERVATION_RELEASE_REQUESTED = "stock.reservation.release.requested";
    public static final String STOCK_RESERVATION_CONFIRMED = "stock.reservation.confirmed";
    public static final String STOCK_RESERVATION_RELEASED = "stock.reservation.released";

    /**
     * 선착순 접수 요청. 파티션 키가 상품이라 같은 상품의 접수는 한 파티션에 모이고,
     * 파티션 안에서 offset이 곧 도착 순번이 된다. 순번을 별도 저장소에 두지 않는 이유다.
     */
    public static final String FLASH_RESERVE_REQUESTED = "flash.reserve.requested";

    /** 확보 결과를 접수 측에 돌려준다. 성공만 발행한다 — 실패는 발행하지 않는다. */
    public static final String FLASH_RESERVE_RESULT = "flash.reserve.result";

    /**
     * 재고가 소진됐다는 신호. 상품당 한 번만 발행된다.
     *
     * <p>접수 파드들이 이걸 받아 로컬 플래그를 세우고, 그 뒤 요청은 Kafka 에 발행조차 하지 않고
     * 즉시 거절한다. 이 신호가 없으면 소진 뒤에 온 요청까지 전부 토픽에 쌓이고, 그만큼 사용자가
     * 결과를 기다리는 시간이 늘어난다. 큐는 스파이크를 <b>기다리게 만들어서</b> 흡수하므로,
     * 받아들인 건수가 곧 통보 지연이다.
     *
     * <p>접수 파드마다 <b>서로 다른 group.id</b> 로 구독해야 한다. 같은 그룹이면 파티션이
     * 나뉘어 한 파드만 신호를 받고 나머지는 계속 발행한다.
     */
    public static final String FLASH_SALE_SOLD_OUT = "flash.sale.sold-out";

    public static final String CUSTOMER_REGISTERED = "customer.registered";
}
