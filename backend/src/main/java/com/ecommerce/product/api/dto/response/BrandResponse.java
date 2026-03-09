package com.ecommerce.product.api.dto.response;

import com.ecommerce.product.domain.model.Brand;

import java.time.LocalDateTime;

public record BrandResponse(
        Long id,
        String publicId,
        String name,
        String slug,
        String countryOfOrigin,
        String styleCategory,
        Integer foundedYear,
        String description,
        String logoUrl,
        LocalDateTime createdAt
) {
    public static BrandResponse from(Brand brand) {
        return new BrandResponse(
                brand.getId(),
                brand.getPublicId(),
                brand.getName(),
                brand.getSlug(),
                brand.getCountryOfOrigin(),
                brand.getStyleCategory(),
                brand.getFoundedYear(),
                brand.getDescription(),
                brand.getLogoUrl(),
                brand.getCreatedAt()
        );
    }
}
