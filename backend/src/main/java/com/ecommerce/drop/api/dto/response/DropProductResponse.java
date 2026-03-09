package com.ecommerce.drop.api.dto.response;

import com.ecommerce.drop.domain.model.DropProduct;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DropProductResponse(
        String publicId,
        Long productVariantId,
        int allocatedQuantity,
        int soldQuantity,
        BigDecimal dropPriceAmount,
        String dropPriceCurrency,
        LocalDateTime createdAt
) {
    public static DropProductResponse from(DropProduct product) {
        return new DropProductResponse(
                product.getPublicId(),
                product.getProductVariantId(),
                product.getAllocatedQuantity(),
                product.getSoldQuantity(),
                product.getDropPriceAmount(),
                product.getDropPriceCurrency(),
                product.getCreatedAt()
        );
    }
}
