package com.ecommerce.order.domain.service;

import com.ecommerce.order.application.dto.ProductSnapshotDto;

public interface ProductCatalogPort {
    boolean existsVariant(Long variantId);
    ProductSnapshotDto fetchSnapshot(Long variantId);
    void reserveStock(Long variantId, int quantity);
    void releaseStock(Long variantId, int quantity);
}
