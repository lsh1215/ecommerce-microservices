package com.ecommerce.product.api.dto.request;

import com.ecommerce.product.domain.model.ProductStatus;
import java.math.BigDecimal;

public record UpdateProductRequest(
        String name,
        String description,
        BigDecimal price,
        String category,
        ProductStatus status
) {
}
