package com.ecommerce.drop.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AddDropProductRequest(
        @NotNull Long productVariantId,
        @Positive int allocatedQuantity,
        @NotNull @Positive BigDecimal dropPriceAmount,
        @NotNull @Size(min = 3, max = 3) String dropPriceCurrency
) {}
