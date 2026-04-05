package com.ecommerce.product.api.dto.request;

import java.math.BigDecimal;

public record ProductSearchRequest(
        String keyword,
        Long brandId,
        String category,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}
