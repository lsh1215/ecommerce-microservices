package com.ecommerce.order.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.order.api.dto.request.CancelOrderRequest;
import com.ecommerce.order.api.dto.request.CreateOrderRequest;
import com.ecommerce.order.api.dto.response.OrderResponse;
import com.ecommerce.order.application.dto.CreateOrderCommand;
import com.ecommerce.order.application.dto.OrderItemCommand;
import com.ecommerce.order.application.dto.ShippingAddressCommand;
import com.ecommerce.order.application.service.OrderService;
import com.ecommerce.order.domain.model.Order;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> createOrder(
            @RequestHeader("X-Customer-Id") Long customerId,
            @Valid @RequestBody CreateOrderRequest request) {
        // customerId comes from the trusted header populated by Traefik's
        // forwardAuth middleware (which calls service-customer's /internal/verify).
        // The body field is ignored — clients can omit it.
        CreateOrderCommand command = new CreateOrderCommand(
                customerId,
                request.items().stream()
                        .map(item -> new OrderItemCommand(
                                item.productVariantId(), item.productId(), item.productName(),
                                item.size(), item.color(), item.unitPrice(), item.quantity()))
                        .toList(),
                new ShippingAddressCommand(
                        request.shippingAddress().recipientName(),
                        request.shippingAddress().phone(),
                        request.shippingAddress().zipCode(),
                        request.shippingAddress().address1(),
                        request.shippingAddress().address2()),
                request.memo()
        );
        Order order = orderService.createOrder(command);
        return ApiResponse.created(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable Long id) {
        Order order = orderService.getOrder(id);
        return ApiResponse.ok(OrderResponse.from(order));
    }

    @GetMapping("/my")
    public ApiResponse<PageResponse<OrderResponse>> getMyOrders(
            @RequestParam Long customerId, Pageable pageable) {
        Page<Order> orders = orderService.getMyOrders(customerId, pageable);
        Page<OrderResponse> responsePage = orders.map(OrderResponse::from);
        return ApiResponse.ok(PageResponse.from(responsePage));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(
            @PathVariable Long id,
            @RequestBody(required = false) CancelOrderRequest request) {
        Order order = orderService.cancelOrder(id);
        return ApiResponse.ok(OrderResponse.from(order));
    }
}
