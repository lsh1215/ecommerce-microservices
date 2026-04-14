package com.ecommerce.product.application.dto;

public record UpdateBrandCommand(
        String name,
        String description,
        String logoUrl,
        String country
) {}
