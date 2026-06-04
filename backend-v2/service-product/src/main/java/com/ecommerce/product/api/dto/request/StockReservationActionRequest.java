package com.ecommerce.product.api.dto.request;

import jakarta.validation.constraints.NotNull;

public record StockReservationActionRequest(
        @NotNull Long orderId
) {
}
