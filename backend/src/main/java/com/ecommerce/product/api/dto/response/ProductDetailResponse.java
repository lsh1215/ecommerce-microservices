package com.ecommerce.product.api.dto.response;

import com.ecommerce.product.domain.model.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductDetailResponse(
        Long id,
        String publicId,
        String slug,
        String category,
        String era,
        BigDecimal basePriceAmount,
        String basePriceCurrency,
        BigDecimal priceUsd,
        BigDecimal priceKrw,
        BigDecimal priceJpy,
        BigDecimal fabricWeightOz,
        String fabricType,
        String fabricWeave,
        String brandName,
        String brandSlug,
        LocalDateTime createdAt,
        List<ProductVariantResponse> variants,
        List<ProductTranslationResponse> translations,
        List<ProductImageResponse> images
) {
    public static ProductDetailResponse from(Product product) {
        return new ProductDetailResponse(
                product.getId(),
                product.getPublicId(),
                product.getSlug(),
                product.getCategory(),
                product.getEra(),
                product.getBasePriceAmount(),
                product.getBasePriceCurrency(),
                product.getPriceUsd(),
                product.getPriceKrw(),
                product.getPriceJpy(),
                product.getFabricWeightOz(),
                product.getFabricType(),
                product.getFabricWeave(),
                product.getBrand().getName(),
                product.getBrand().getSlug(),
                product.getCreatedAt(),
                product.getVariants().stream().map(ProductVariantResponse::from).toList(),
                product.getTranslations().stream().map(ProductTranslationResponse::from).toList(),
                product.getImages().stream().map(ProductImageResponse::from).toList()
        );
    }
}
