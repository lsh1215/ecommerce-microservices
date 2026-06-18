package com.ecommerce.product.api.dto.response;

import com.ecommerce.product.application.dto.ProductListItemResult;
import com.ecommerce.product.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String name,
        BigDecimal price,
        ProductStatus status,
        String brandName,
        String category,
        String primaryImageUrl,
        LocalDateTime createdAt
) {

    public static ProductResponse from(ProductListItemResult product) {
        return new ProductResponse(
                product.id(),
                product.name(),
                product.price(),
                product.status(),
                product.brandName(),
                product.category(),
                product.primaryImageUrl(),
                product.createdAt()
        );
    }
}
