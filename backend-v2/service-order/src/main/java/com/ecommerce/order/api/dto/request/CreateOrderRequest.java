package com.ecommerce.order.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
        @NotNull Long customerId,
        @NotNull @Size(min = 1) List<@Valid OrderItemRequest> items,
        @NotNull @Valid ShippingAddressRequest shippingAddress,
        String memo
) {
}
