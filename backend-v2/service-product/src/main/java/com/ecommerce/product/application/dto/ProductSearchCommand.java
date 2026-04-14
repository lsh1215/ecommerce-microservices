package com.ecommerce.product.application.dto;

import java.math.BigDecimal;

public record ProductSearchCommand(
        String keyword,
        Long brandId,
        String category,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {}
