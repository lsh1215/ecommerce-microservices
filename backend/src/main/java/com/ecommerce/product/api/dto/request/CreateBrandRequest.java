package com.ecommerce.product.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBrandRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String slug,
        @Size(max = 2) String countryOfOrigin,
        String styleCategory,
        Integer foundedYear,
        String description,
        @Size(max = 500) String logoUrl
) {}
