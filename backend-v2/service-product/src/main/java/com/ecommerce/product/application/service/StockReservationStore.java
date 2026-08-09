package com.ecommerce.product.application.service;

import java.util.Optional;

public interface StockReservationStore {

    boolean reserve(Long variantId, Long orderId, int quantity, int availableStock);

    void release(Long variantId, Long orderId);

    /**
     * Reads the admitted reservation quantity for (variantId, orderId) without mutating
     * the store. Used to reconstruct a not-yet-settled {@code stock_reservation} row in
     * async settle mode when the DB row hasn't been materialized yet.
     */
    Optional<Integer> findReservedQuantity(Long variantId, Long orderId);

    /**
     * Preloads the DB stock snapshot into Redis so subsequent {@link #reserveRedisOnly}
     * calls can admit reservations without any DB read. Overwrites any previous value.
     */
    void preloadAvailable(Long variantId, long available);

    /**
     * Pure Redis admit path for async settle mode: no DB reads or writes. Requires
     * {@link #preloadAvailable} to have been called for the variant first.
     *
     * @return 1 admitted, 0 capacity exceeded, 2 duplicate (already admitted for this
     *         order), -1 not preloaded (caller must fall back to the DB-backed path).
     */
    int reserveRedisOnly(Long variantId, Long orderId, int quantity);
}
