package com.ecommerce.order.application.dto;

import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResult(
        Long id,
        Long customerId,
        String orderNumber,
        OrderStatus status,
        BigDecimal totalAmount,
        ShippingAddressResult shippingAddress,
        String memo,
        List<OrderItemResult> items,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static OrderDetailResult from(Order order) {
        return new OrderDetailResult(
                order.getId(),
                order.getCustomerId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalAmount(),
                ShippingAddressResult.from(order.getShippingAddress()),
                order.getMemo(),
                order.getItems().stream()
                        .map(OrderItemResult::from)
                        .toList(),
                order.getExpiresAt(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
