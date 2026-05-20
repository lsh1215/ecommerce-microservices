package com.ecommerce.product.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.product.api.dto.request.StockReserveRequest;
import com.ecommerce.product.api.dto.response.ProductVariantResponse;
import com.ecommerce.product.api.dto.response.VariantDetailResponse;
import com.ecommerce.product.application.service.ProductService;
import com.ecommerce.product.domain.model.ProductVariant;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/products")
@RequiredArgsConstructor
public class InternalProductController {

    private final ProductService productService;

    @GetMapping("/variants/{variantId}")
    public ApiResponse<VariantDetailResponse> getVariantDetail(@PathVariable Long variantId) {
        ProductVariant variant = productService.getVariantDetail(variantId);
        return ApiResponse.ok(VariantDetailResponse.from(variant));
    }

    @PostMapping("/variants/{variantId}/reserve-stock")
    public ApiResponse<ProductVariantResponse> reserveStock(
            @PathVariable Long variantId,
            @Valid @RequestBody StockReserveRequest request) {
        ProductVariant variant = request.orderId() == null
                ? productService.reserveStock(variantId, request.quantity())
                : productService.reserveStock(request.orderId(), variantId, request.quantity());
        return ApiResponse.ok(ProductVariantResponse.from(variant));
    }

    @PostMapping("/variants/{variantId}/release-stock")
    public ApiResponse<ProductVariantResponse> releaseStock(
            @PathVariable Long variantId,
            @Valid @RequestBody StockReserveRequest request) {
        ProductVariant variant = request.orderId() == null
                ? productService.releaseStock(variantId, request.quantity())
                : productService.releaseReservation(request.orderId(), variantId);
        return ApiResponse.ok(ProductVariantResponse.from(variant));
    }
}
