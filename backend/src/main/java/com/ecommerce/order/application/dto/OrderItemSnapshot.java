package com.ecommerce.order.application.dto;

import java.math.BigDecimal;

public record OrderItemSnapshot(
        Long productVariantId,
        String productName,
        String brandName,
        BigDecimal unitPriceAmount,
        String unitPriceCurrency,
        String sizeLabel,
        String sku
) {
}
