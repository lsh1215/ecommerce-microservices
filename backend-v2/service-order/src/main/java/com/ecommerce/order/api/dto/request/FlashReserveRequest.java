package com.ecommerce.order.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FlashReserveRequest(
        @NotNull Long variantId,
        @NotNull @Min(1) Integer quantity) {
}
