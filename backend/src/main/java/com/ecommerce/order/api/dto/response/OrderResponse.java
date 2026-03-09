package com.ecommerce.order.api.dto.response;

import com.ecommerce.order.domain.model.Orders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String publicId,
        Long customerId,
        String status,
        BigDecimal totalAmount,
        String totalCurrency,
        String shippingAddress,
        List<OrderItemResponse> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static OrderResponse from(Orders order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getPublicId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getTotalCurrency(),
                order.getShippingAddress(),
                itemResponses,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
