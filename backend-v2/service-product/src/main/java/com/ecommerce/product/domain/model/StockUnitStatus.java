package com.ecommerce.product.domain.model;

/** Shopify식 재고 유닛의 상태. AVAILABLE ↔ RESERVED → CONFIRMED (release는 RESERVED→AVAILABLE). */
public enum StockUnitStatus {
    AVAILABLE,
    RESERVED,
    CONFIRMED
}
