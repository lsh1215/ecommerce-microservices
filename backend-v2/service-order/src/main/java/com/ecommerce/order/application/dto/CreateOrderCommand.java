package com.ecommerce.order.application.dto;

import java.util.List;

public record CreateOrderCommand(
        Long customerId,
        List<OrderItemCommand> items,
        ShippingAddressCommand shippingAddress,
        String memo
) {}
