package com.ecommerce.product.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateVariantRequest(
        @NotBlank String size,
        @NotBlank String color,
        @NotBlank String sku,
        @NotNull @Min(0) Integer stockQuantity,
        BigDecimal price
) {
}
