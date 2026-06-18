package com.ecommerce.order.api.dto.response;

import com.ecommerce.order.application.dto.OrderDetailResult;
import com.ecommerce.order.domain.model.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        Long customerId,
        String orderNumber,
        OrderStatus status,
        BigDecimal totalAmount,
        ShippingAddressResponse shippingAddress,
        String memo,
        List<OrderItemResponse> items,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static OrderResponse from(OrderDetailResult order) {
        List<OrderItemResponse> itemResponses = order.items().stream()
                .map(OrderItemResponse::from)
                .toList();

        return new OrderResponse(
                order.id(),
                order.customerId(),
                order.orderNumber(),
                order.status(),
                order.totalAmount(),
                ShippingAddressResponse.from(order.shippingAddress()),
                order.memo(),
                itemResponses,
                order.expiresAt(),
                order.createdAt(),
                order.updatedAt()
        );
    }
}
