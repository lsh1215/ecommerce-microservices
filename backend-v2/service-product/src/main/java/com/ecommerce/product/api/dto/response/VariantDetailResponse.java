package com.ecommerce.product.api.dto.response;

import java.math.BigDecimal;

public record VariantDetailResponse(
        Long productId,
        Long variantId,
        String productName,
        String size,
        String color,
        BigDecimal unitPrice,
        int stockQuantity
) {}
