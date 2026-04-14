package com.ecommerce.product.api.dto.response;

import com.ecommerce.product.domain.model.ProductVariant;
import java.math.BigDecimal;

public record VariantDetailResponse(
        Long productId,
        Long variantId,
        String productName,
        String size,
        String color,
        BigDecimal unitPrice,
        int stockQuantity
) {

    public static VariantDetailResponse from(ProductVariant variant) {
        return new VariantDetailResponse(
                variant.getProduct().getId(),
                variant.getId(),
                variant.getProduct().getName(),
                variant.getSize(),
                variant.getColor(),
                variant.effectivePrice(),
                variant.getStockQuantity()
        );
    }
}
