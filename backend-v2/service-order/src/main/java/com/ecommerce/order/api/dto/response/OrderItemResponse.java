package com.ecommerce.order.api.dto.response;

import com.ecommerce.order.domain.model.OrderItem;
import java.math.BigDecimal;

public record OrderItemResponse(
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

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
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
