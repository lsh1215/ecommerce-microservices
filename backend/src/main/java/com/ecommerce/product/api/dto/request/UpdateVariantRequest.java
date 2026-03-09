package com.ecommerce.product.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateVariantRequest(
        @NotBlank @Size(max = 100) String sku,
        @NotBlank @Size(max = 20) String sizeLabel,
        @Size(max = 50) String colorName,
        @Size(max = 7) String colorHex,
        BigDecimal priceOverrideAmount,
        @Size(max = 3) String priceOverrideCurrency,
        BigDecimal measChestCm,
        BigDecimal measShoulderCm,
        BigDecimal measSleeveCm,
        BigDecimal measBodyLengthCm,
        BigDecimal measWaistCm,
        BigDecimal measInseamCm,
        BigDecimal measThighCm,
        BigDecimal measHemCm
) {}
