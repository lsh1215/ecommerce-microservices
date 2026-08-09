package com.ecommerce.product.infra.redis;

/**
 * Shared Redis key naming for the stock-reservation admit path and the async
 * settler that drains it. Kept in one place so {@link RedisStockReservationStore}
 * (writer) and {@link StockReservationSettler} (reader) never drift.
 */
final class RedisReservationKeys {

    /** Global SET of variantIds with a non-empty settle queue, maintained by the reserve Lua. */
    static final String ACTIVE_VARIANTS_KEY = "stock:reservation:active-variants";

    private RedisReservationKeys() {
    }

    static String reservationsKey(Long variantId) {
        return "stock:reservation:" + variantId + ":orders";
    }

    static String totalKey(Long variantId) {
        return "stock:reservation:" + variantId + ":total";
    }

    /** Preloaded DB stock snapshot enabling the async, Redis-only reserve path (no DB read). */
    static String availableKey(Long variantId) {
        return "stock:reservation:" + variantId + ":available";
    }

    /** Durable settle queue: RPUSH'd atomically by the reserve Lua on a successful admit. */
    static String settleQueueKey(Long variantId) {
        return "stock:reservation:" + variantId + ":settle";
    }

    /** In-flight items moved out of the settle queue while the settler materializes them. */
    static String processingKey(Long variantId) {
        return "stock:reservation:" + variantId + ":settle:processing";
    }

    /** Dead-letter list for items whose DB insert failed after being popped for settling. */
    static String settleDlqKey(Long variantId) {
        return "stock:reservation:" + variantId + ":settle:dlq";
    }

    /** Per-item retry-attempt counters (hash: field=item, value=attempt count) for the settler. */
    static String settleAttemptsKey(Long variantId) {
        return "stock:reservation:" + variantId + ":settle:attempts";
    }
}
