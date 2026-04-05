package com.ecommerce.product.api.dto.response;

import com.ecommerce.product.domain.model.ProductImage;

public record ProductImageResponse(
        Long id,
        String url,
        int sortOrder,
        boolean isPrimary
) {

    public static ProductImageResponse from(ProductImage image) {
        return new ProductImageResponse(
                image.getId(),
                image.getUrl(),
                image.getSortOrder(),
                image.isPrimary()
        );
    }
}
