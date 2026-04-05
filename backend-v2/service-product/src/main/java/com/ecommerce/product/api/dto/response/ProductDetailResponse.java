package com.ecommerce.product.api.dto.response;

import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductDetailResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        ProductStatus status,
        BrandResponse brand,
        String category,
        List<ProductVariantResponse> variants,
        List<ProductImageResponse> images,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ProductDetailResponse from(Product product) {
        BrandResponse brandResponse = product.getBrand() != null
                ? BrandResponse.from(product.getBrand())
                : null;

        List<ProductVariantResponse> variantResponses = product.getVariants().stream()
                .map(ProductVariantResponse::from)
                .toList();

        List<ProductImageResponse> imageResponses = product.getImages().stream()
                .map(ProductImageResponse::from)
                .toList();

        return new ProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStatus(),
                brandResponse,
                product.getCategory(),
                variantResponses,
                imageResponses,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
