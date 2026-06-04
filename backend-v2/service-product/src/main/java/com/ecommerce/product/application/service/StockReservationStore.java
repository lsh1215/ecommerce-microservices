package com.ecommerce.product.application.service;

public interface StockReservationStore {

    boolean reserve(Long variantId, Long orderId, int quantity, int availableStock);

    void release(Long variantId, Long orderId);
}
