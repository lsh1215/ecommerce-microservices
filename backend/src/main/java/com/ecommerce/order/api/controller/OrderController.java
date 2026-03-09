package com.ecommerce.order.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.order.api.dto.request.CreateOrderRequest;
import com.ecommerce.order.api.dto.response.OrderResponse;
import com.ecommerce.order.application.service.OrderService;
import com.ecommerce.order.application.usecase.CancelOrderUseCase;
import com.ecommerce.order.application.usecase.CreateOrderUseCase;
import com.ecommerce.order.domain.model.Orders;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        Orders order = createOrderUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(OrderResponse.from(order)));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getByPublicId(@PathVariable String publicId) {
        Orders order = orderService.getByPublicId(publicId);
        return ResponseEntity.ok(ApiResponse.success(OrderResponse.from(order)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> listByCustomerId(
            @RequestParam Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Orders> result = orderService.listByCustomerId(customerId, PageRequest.of(page, size));
        PageResponse<OrderResponse> pageResponse = PageResponse.from(result, OrderResponse::from);
        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }

    @PostMapping("/{publicId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable String publicId,
            @RequestParam(required = false) String reason) {
        Orders order = cancelOrderUseCase.execute(publicId, reason);
        return ResponseEntity.ok(ApiResponse.success(OrderResponse.from(order)));
    }
}
