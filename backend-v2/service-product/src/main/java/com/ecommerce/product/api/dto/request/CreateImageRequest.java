package com.ecommerce.product.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateImageRequest(
        @NotBlank String url,
        Integer sortOrder,
        Boolean isPrimary
) {
}
