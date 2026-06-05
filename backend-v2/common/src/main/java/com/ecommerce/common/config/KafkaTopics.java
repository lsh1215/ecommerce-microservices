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

    public static final String CUSTOMER_REGISTERED = "customer.registered";
}
