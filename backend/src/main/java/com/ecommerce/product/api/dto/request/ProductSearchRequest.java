package com.ecommerce.product.api.dto.request;

import java.math.BigDecimal;

public record ProductSearchRequest(
        Long brandId,
        String category,
        String era,
        String fabricType,
        String fabricWeave,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        int page,
        int size,
        String sort,
        String direction
) {
    public ProductSearchRequest {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (sort == null || sort.isBlank()) sort = "createdAt";
        if (direction == null || direction.isBlank()) direction = "desc";
    }
}
