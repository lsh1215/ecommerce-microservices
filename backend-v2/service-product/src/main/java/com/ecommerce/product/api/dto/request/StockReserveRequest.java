package com.ecommerce.product.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockReserveRequest(
        @NotNull @Min(1) Integer quantity
) {
}
