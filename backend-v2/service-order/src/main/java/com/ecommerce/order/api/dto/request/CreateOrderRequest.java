package com.ecommerce.order.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
        // Optional — for backward compatibility only. The authoritative
        // customerId comes from the X-Customer-Id header populated by
        // Traefik's forwardAuth middleware. If both are present the
        // header wins.
        Long customerId,
        @NotNull @Size(min = 1) List<@Valid OrderItemRequest> items,
        @NotNull @Valid ShippingAddressRequest shippingAddress,
        String memo
) {
}
