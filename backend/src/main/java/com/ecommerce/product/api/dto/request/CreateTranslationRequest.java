package com.ecommerce.product.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTranslationRequest(
        @NotBlank @Size(max = 2) String locale,
        @NotBlank @Size(max = 255) String name,
        String description
) {}
