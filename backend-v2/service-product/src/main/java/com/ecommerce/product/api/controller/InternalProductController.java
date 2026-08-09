package com.ecommerce.product.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.product.api.dto.request.StockReserveRequest;
import com.ecommerce.product.api.dto.request.StockReservationActionRequest;
import com.ecommerce.product.api.dto.response.ProductVariantResponse;
import com.ecommerce.product.api.dto.response.ReserveResultResponse;
import com.ecommerce.product.api.dto.response.VariantDetailResponse;
import com.ecommerce.product.application.service.ProductService;
import com.ecommerce.product.application.service.FlashReserveService;
import com.ecommerce.product.domain.model.ProductVariant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/products")
@RequiredArgsConstructor
public class InternalProductController {

    private final ProductService productService;
    private final FlashReserveService flashReserveService;

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

    /**
     * 부하 테스트가 async 모드에서 Redis-only reserve 경로를 즉시 타도록 DB 재고를
     * Redis에 사전 적재한다. 예약 로직 자체에는 관여하지 않는다.
     */
    @PostMapping("/variants/{variantId}/preload-reservation")
    public ApiResponse<Void> preloadReservation(@PathVariable Long variantId) {
        productService.preloadReservationStock(variantId);
        return ApiResponse.ok(null);
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

    @PostMapping("/variants/{variantId}/confirm-reservation")
    public ApiResponse<ProductVariantResponse> confirmReservation(
            @PathVariable Long variantId,
            @Valid @RequestBody StockReservationActionRequest request) {
        ProductVariant variant = productService.confirmReservation(request.orderId(), variantId);
        return ApiResponse.ok(ProductVariantResponse.from(variant));
    }

    /** Shopify식 동기 예약 — SKIP LOCKED로 AVAILABLE 유닛을 그 자리에서 확보. 200 GRANTED / 409 SOLD_OUT. */
    @PostMapping("/variants/{variantId}/reserve-unit")
    public ResponseEntity<ApiResponse<ReserveResultResponse>> reserveUnit(
            @PathVariable Long variantId,
            @Valid @RequestBody StockReserveRequest request) {
        boolean granted = flashReserveService.reserve(request.orderId(), variantId, request.quantity());
        if (!granted) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.ok(new ReserveResultResponse("SOLD_OUT")));
        }
        return ResponseEntity.ok(ApiResponse.ok(new ReserveResultResponse("GRANTED")));
    }

    /** 결제 성공: 해당 주문의 RESERVED 유닛을 CONFIRMED로 확정(영구 소진). */
    @PostMapping("/variants/{variantId}/confirm-unit")
    public ApiResponse<ReserveResultResponse> confirmUnit(
            @PathVariable Long variantId, @RequestParam Long orderId) {
        boolean ok = flashReserveService.confirm(orderId, variantId);
        return ApiResponse.ok(new ReserveResultResponse(ok ? "CONFIRMED" : "NOT_FOUND"));
    }

    /** 결제 실패·보상: 해당 주문의 RESERVED 유닛을 AVAILABLE로 반납. */
    @PostMapping("/variants/{variantId}/release-unit")
    public ApiResponse<ReserveResultResponse> releaseUnit(
            @PathVariable Long variantId, @RequestParam Long orderId) {
        boolean ok = flashReserveService.release(orderId, variantId);
        return ApiResponse.ok(new ReserveResultResponse(ok ? "RELEASED" : "NOT_FOUND"));
    }
}
