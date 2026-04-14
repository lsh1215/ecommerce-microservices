package com.ecommerce.product.application.dto;

public record CreateBrandCommand(
        String name,
        String description,
        String logoUrl,
        String country
) {}
