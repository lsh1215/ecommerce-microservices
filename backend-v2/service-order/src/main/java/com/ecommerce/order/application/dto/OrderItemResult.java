package com.ecommerce.order.application.dto;

import com.ecommerce.order.domain.model.OrderItem;
import java.math.BigDecimal;

public record OrderItemResult(
        Long id,
        Long productId,
        Long productVariantId,
        String productName,
        String size,
        String color,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal totalPrice
) {

    public static OrderItemResult from(OrderItem item) {
        return new OrderItemResult(
                item.getId(),
                item.getVariantSnapshot().getProductId(),
                item.getVariantSnapshot().getProductVariantId(),
                item.getVariantSnapshot().getProductName(),
                item.getVariantSnapshot().getSize(),
                item.getVariantSnapshot().getColor(),
                item.getVariantSnapshot().getUnitPrice(),
                item.getQuantity(),
                item.getTotalPrice()
        );
    }
}
