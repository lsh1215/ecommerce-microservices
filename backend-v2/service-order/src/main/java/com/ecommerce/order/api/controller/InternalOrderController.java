package com.ecommerce.order.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.order.api.dto.response.OrderResponse;
import com.ecommerce.order.application.dto.OrderDetailResult;
import com.ecommerce.order.application.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;

    @PostMapping("/{id}/mark-paid")
    public ApiResponse<OrderResponse> markPaid(@PathVariable Long id) {
        OrderDetailResult order = orderService.markPaid(id);
        return ApiResponse.ok(OrderResponse.from(order));
    }

    @PostMapping("/{id}/mark-confirmed")
    public ApiResponse<OrderResponse> markConfirmed(@PathVariable Long id) {
        OrderDetailResult order = orderService.markConfirmed(id);
        return ApiResponse.ok(OrderResponse.from(order));
    }
}
