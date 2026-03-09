package com.ecommerce.product.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductRequest(
        @NotNull Long brandId,
        @Size(max = 150) String slug,
        @NotBlank @Size(max = 50) String category,
        @Size(max = 50) String era,
        @NotNull BigDecimal basePriceAmount,
        @NotBlank @Size(max = 3) String basePriceCurrency,
        BigDecimal priceUsd,
        BigDecimal priceKrw,
        BigDecimal priceJpy,
        BigDecimal fabricWeightOz,
        @Size(max = 50) String fabricType,
        @Size(max = 50) String fabricWeave,
        @NotBlank @Size(max = 255) String name
) {}
