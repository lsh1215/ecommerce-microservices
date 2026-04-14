package com.ecommerce.product.application.dto;

import java.math.BigDecimal;

public record CreateProductCommand(
        String name,
        String description,
        BigDecimal price,
        Long brandId,
        String category
) {}
