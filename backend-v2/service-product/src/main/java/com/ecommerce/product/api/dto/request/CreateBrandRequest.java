package com.ecommerce.product.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateBrandRequest(
        @NotBlank String name,
        String description,
        String logoUrl,
        String country
) {
}
