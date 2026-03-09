package com.ecommerce.product.api.dto.response;

import com.ecommerce.product.domain.model.ProductTranslation;

public record ProductTranslationResponse(
        Long id,
        String locale,
        String name,
        String description
) {
    public static ProductTranslationResponse from(ProductTranslation translation) {
        return new ProductTranslationResponse(
                translation.getId(),
                translation.getLocale(),
                translation.getName(),
                translation.getDescription()
        );
    }
}
