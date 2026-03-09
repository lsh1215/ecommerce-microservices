package com.ecommerce.order.api.dto.response;

import com.ecommerce.order.domain.model.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long productVariantId,
        int quantity,
        String productName,
        String brandName,
        BigDecimal unitPriceAmount,
        String unitPriceCurrency,
        String sizeLabel,
        String sku,
        BigDecimal subtotal
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProductVariantId(),
                item.getQuantity(),
                item.getProductName(),
                item.getBrandName(),
                item.getUnitPriceAmount(),
                item.getUnitPriceCurrency(),
                item.getSizeLabel(),
                item.getSku(),
                item.subtotal()
        );
    }
}
