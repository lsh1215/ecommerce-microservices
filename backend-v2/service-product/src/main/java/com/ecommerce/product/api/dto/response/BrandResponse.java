package com.ecommerce.product.api.dto.response;

import com.ecommerce.product.domain.model.Brand;

public record BrandResponse(
        Long id,
        String name,
        String description,
        String logoUrl,
        String country
) {

    public static BrandResponse from(Brand brand) {
        return new BrandResponse(
                brand.getId(),
                brand.getName(),
                brand.getDescription(),
                brand.getLogoUrl(),
                brand.getCountry()
        );
    }
}
