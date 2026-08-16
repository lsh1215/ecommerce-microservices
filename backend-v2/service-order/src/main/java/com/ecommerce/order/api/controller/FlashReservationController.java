package com.ecommerce.order.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.order.api.dto.request.FlashReserveRequest;
import com.ecommerce.order.api.dto.response.FlashReservationResponse;
import com.ecommerce.order.application.service.FlashReservationService;
import com.ecommerce.order.domain.model.FlashReservation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 선착순 예약 접수/조회. 접수는 비동기(202 Accepted) — 예약 레코드를 durable하게 남기고 즉시 반환하며,
 * 실제 유닛 확보는 granter가 도착 순서대로 처리해 결과를 이벤트로 되돌린다.
 */
@RestController
@RequestMapping("/api/orders/flash-reserve")
@RequiredArgsConstructor
public class FlashReservationController {

    private final FlashReservationService flashReservationService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<FlashReservationResponse> submit(
            @RequestHeader("X-Customer-Id") Long customerId,
            @Valid @RequestBody FlashReserveRequest request) {
        FlashReservation reservation = flashReservationService.submit(
                customerId, request.variantId(), request.quantity());
        return ApiResponse.ok(FlashReservationResponse.from(reservation));
    }

    @GetMapping("/{reservationId}")
    public ApiResponse<FlashReservationResponse> get(@PathVariable Long reservationId) {
        return ApiResponse.ok(FlashReservationResponse.from(flashReservationService.get(reservationId)));
    }
}
