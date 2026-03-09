package com.ecommerce.product.api.dto.response;

import com.ecommerce.product.domain.model.ProductVariant;

import java.math.BigDecimal;

public record ProductVariantResponse(
        Long id,
        String publicId,
        String sku,
        String sizeLabel,
        String colorName,
        String colorHex,
        BigDecimal priceOverrideAmount,
        String priceOverrideCurrency,
        BigDecimal measChestCm,
        BigDecimal measShoulderCm,
        BigDecimal measSleeveCm,
        BigDecimal measBodyLengthCm,
        BigDecimal measWaistCm,
        BigDecimal measInseamCm,
        BigDecimal measThighCm,
        BigDecimal measHemCm
) {
    public static ProductVariantResponse from(ProductVariant variant) {
        return new ProductVariantResponse(
                variant.getId(),
                variant.getPublicId(),
                variant.getSku(),
                variant.getSizeLabel(),
                variant.getColorName(),
                variant.getColorHex(),
                variant.getPriceOverrideAmount(),
                variant.getPriceOverrideCurrency(),
                variant.getMeasChestCm(),
                variant.getMeasShoulderCm(),
                variant.getMeasSleeveCm(),
                variant.getMeasBodyLengthCm(),
                variant.getMeasWaistCm(),
                variant.getMeasInseamCm(),
                variant.getMeasThighCm(),
                variant.getMeasHemCm()
        );
    }
}
