package com.ecommerce.order.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.order.api.dto.request.CancelOrderRequest;
import com.ecommerce.order.api.dto.request.CreateOrderRequest;
import com.ecommerce.order.api.dto.response.OrderResponse;
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
    public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
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
