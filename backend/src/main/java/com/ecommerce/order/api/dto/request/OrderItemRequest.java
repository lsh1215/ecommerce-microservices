package com.ecommerce.order.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
        @NotNull(message = "Product variant ID is required")
        Long productVariantId,

        @Min(value = 1, message = "Quantity must be at least 1")
        int quantity
) {
}
