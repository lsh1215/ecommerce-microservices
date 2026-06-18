package com.ecommerce.product.application.dto;

import com.ecommerce.product.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductListItemResult(
        Long id,
        String name,
        BigDecimal price,
        ProductStatus status,
        String brandName,
        String category,
        String primaryImageUrl,
        LocalDateTime createdAt
) {
}
