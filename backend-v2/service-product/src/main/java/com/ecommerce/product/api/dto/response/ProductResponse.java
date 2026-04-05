package com.ecommerce.product.api.dto.response;

import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String name,
        BigDecimal price,
        ProductStatus status,
        String brandName,
        String category,
        String primaryImageUrl,
        LocalDateTime createdAt
) {

    public static ProductResponse from(Product product) {
        String primaryUrl = product.getImages().stream()
                .filter(img -> img.isPrimary())
                .findFirst()
                .map(img -> img.getUrl())
                .orElse(null);

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStatus(),
                product.getBrand() != null ? product.getBrand().getName() : null,
                product.getCategory(),
                primaryUrl,
                product.getCreatedAt()
        );
    }
}
