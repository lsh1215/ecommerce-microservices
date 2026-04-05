package com.ecommerce.order.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record OrderItemRequest(
        @NotNull Long productVariantId,
        @NotNull Long productId,
        @NotBlank String productName,
        String size,
        String color,
        @NotNull @DecimalMin("0.01") BigDecimal unitPrice,
        @NotNull @Min(1) Integer quantity
) {
}
