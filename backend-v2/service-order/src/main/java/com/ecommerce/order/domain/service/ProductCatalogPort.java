package com.ecommerce.order.domain.service;

import com.ecommerce.order.application.dto.ProductSnapshotDto;

public interface ProductCatalogPort {
    boolean existsVariant(Long variantId);
    ProductSnapshotDto fetchSnapshot(Long variantId);
    ProductSnapshotDto reserveStockAndFetchSnapshot(Long orderId, Long variantId, int quantity);
    void reserveStock(Long orderId, Long variantId, int quantity);
    void confirmReservation(Long orderId, Long variantId);
    void releaseStock(Long orderId, Long variantId, int quantity);
}
