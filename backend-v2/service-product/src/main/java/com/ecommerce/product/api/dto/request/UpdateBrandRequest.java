package com.ecommerce.product.api.dto.request;

public record UpdateBrandRequest(
        String name,
        String description,
        String logoUrl,
        String country
) {
}
