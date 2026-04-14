package com.ecommerce.order.application.dto;

import java.math.BigDecimal;

public record OrderItemCommand(
        Long productVariantId,
        Long productId,
        String productName,
        String size,
        String color,
        BigDecimal unitPrice,
        int quantity
) {}
