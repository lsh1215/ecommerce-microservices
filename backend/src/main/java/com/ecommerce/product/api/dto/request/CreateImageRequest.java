package com.ecommerce.product.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateImageRequest(
        @NotBlank @Size(max = 500) String url,
        Short sortOrder,
        Boolean isPrimary
) {}
