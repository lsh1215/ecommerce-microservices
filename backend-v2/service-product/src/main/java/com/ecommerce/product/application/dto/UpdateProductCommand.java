package com.ecommerce.product.application.dto;

import com.ecommerce.product.domain.model.ProductStatus;
import java.math.BigDecimal;

public record UpdateProductCommand(
        String name,
        String description,
        BigDecimal price,
        String category,
        ProductStatus status
) {}
