package com.ecommerce.common.config;

public final class KafkaTopics {

    private KafkaTopics() {
    }

    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_CANCELLED = "order.cancelled";

    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";

    public static final String PRODUCT_STOCK_RESERVED = "product.stock-reserved";
    public static final String PRODUCT_STOCK_RELEASED = "product.stock-released";

    public static final String CUSTOMER_REGISTERED = "customer.registered";

    // 선착순 예약 (비동기 · Outbox → Kafka → granter). 요청은 variantId로 파티셔닝해
    // 한 상품이 한 파티션에 도착(offset) 순서로 쌓이고, granter가 그 순서대로 직렬 처리한다.
    public static final String FLASH_RESERVE_REQUESTED = "flash.reserve.requested";
    public static final String FLASH_RESERVE_RESULT = "flash.reserve.result";
}
