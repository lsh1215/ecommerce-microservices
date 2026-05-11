package com.ecommerce.order.api.dto.response;

import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.model.VirtualAccountInstruction;
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
        VirtualAccount virtualAccount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static OrderResponse from(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalAmount(),
                ShippingAddressResponse.from(order.getShippingAddress()),
                order.getMemo(),
                itemResponses,
                order.getExpiresAt(),
                VirtualAccount.from(order.getVirtualAccount()),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public record VirtualAccount(
            String bank,
            String accountNumber,
            String holderName,
            BigDecimal amount,
            LocalDateTime expiresAt
    ) {
        public static VirtualAccount from(VirtualAccountInstruction instruction) {
            if (instruction == null) {
                return null;
            }
            return new VirtualAccount(
                    instruction.getBank(),
                    instruction.getAccountNumber(),
                    instruction.getHolderName(),
                    instruction.getAmount(),
                    instruction.getExpiresAt()
            );
        }
    }
}
