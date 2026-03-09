package com.ecommerce.inventory.api.dto.request;

import jakarta.validation.constraints.NotNull;

public record AdjustStockRequest(
        @NotNull int quantityChange,
        String reason
) {}
