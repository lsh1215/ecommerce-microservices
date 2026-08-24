package com.ecommerce.order.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.order.api.dto.request.FlashReserveRequest;
import com.ecommerce.order.api.dto.response.FlashReservationResponse;
import com.ecommerce.order.application.dto.FlashSubmitResult;
import com.ecommerce.order.application.service.FlashReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

/**
 * 선착순 접수/조회.
 *
 * <p>접수는 Kafka 발행만 하고 202 로 순번(파티션·offset)을 돌려준다. 매진 뒤에 온 요청은
 * 발행하지 않고 즉시 409 로 거절한다. 그래야 앞서 접수된 사람들의 결과 통보가 늦어지지 않는다.
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
        FlashSubmitResult result = flashReservationService.submit(
                customerId, request.variantId(), request.quantity());
        return ApiResponse.ok(
                FlashReservationResponse.accepted(result, request.variantId(), request.quantity()));
    }

    /**
     * {@code variantId} 를 함께 받는 이유는 탈락 판정 때문이다. 승자만 row 로 남기므로,
     * row 가 없을 때 "탈락"인지 "아직 처리 전"인지는 그 상품이 매진됐는지로 갈린다.
     */
    @GetMapping("/{partition}/{offset}")
    public ApiResponse<FlashReservationResponse> get(
            @PathVariable int partition,
            @PathVariable long offset,
            @RequestParam(required = false) Long variantId) {
        return ApiResponse.ok(FlashReservationResponse.from(
                flashReservationService.get(partition, offset, variantId)));
    }
}
