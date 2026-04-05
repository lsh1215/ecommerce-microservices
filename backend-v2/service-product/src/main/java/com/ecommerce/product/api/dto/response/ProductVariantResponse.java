package com.ecommerce.product.api.dto.response;

import com.ecommerce.product.domain.model.ProductVariant;
import java.math.BigDecimal;

public record ProductVariantResponse(
        Long id,
        String size,
        String color,
        String sku,
        int stockQuantity,
        BigDecimal price
) {

    public static ProductVariantResponse from(ProductVariant variant) {
        return new ProductVariantResponse(
                variant.getId(),
                variant.getSize(),
                variant.getColor(),
                variant.getSku(),
                variant.getStockQuantity(),
                variant.getPrice()
        );
    }
}
