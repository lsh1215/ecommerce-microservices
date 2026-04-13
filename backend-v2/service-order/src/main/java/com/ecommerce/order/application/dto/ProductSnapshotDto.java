package com.ecommerce.order.application.dto;

import java.math.BigDecimal;

public record ProductSnapshotDto(
    Long productId,
    Long productVariantId,
    String productName,
    String size,
    String color,
    BigDecimal unitPrice
) {}
