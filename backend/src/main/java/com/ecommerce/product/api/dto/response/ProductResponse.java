package com.ecommerce.product.api.dto.response;

import com.ecommerce.product.domain.model.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
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
        LocalDateTime createdAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
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
                product.getCreatedAt()
        );
    }
}
