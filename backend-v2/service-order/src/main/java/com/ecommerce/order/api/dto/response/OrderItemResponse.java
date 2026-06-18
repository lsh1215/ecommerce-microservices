package com.ecommerce.order.api.dto.response;

import com.ecommerce.order.application.dto.OrderItemResult;
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

    public static OrderItemResponse from(OrderItemResult item) {
        return new OrderItemResponse(
                item.id(),
                item.productId(),
                item.productVariantId(),
                item.productName(),
                item.size(),
                item.color(),
                item.unitPrice(),
                item.quantity(),
                item.totalPrice()
        );
    }
}
