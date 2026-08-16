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

    /** 확보 결과(성공/실패)를 접수 측에 돌려준다. */
    public static final String FLASH_RESERVE_RESULT = "flash.reserve.result";

    public static final String CUSTOMER_REGISTERED = "customer.registered";
}
