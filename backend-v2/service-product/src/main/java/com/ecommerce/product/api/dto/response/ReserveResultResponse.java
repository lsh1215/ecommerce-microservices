package com.ecommerce.product.api.dto.response;

/**
 * 선착순 예약 인테이크/결과 응답. status는 PENDING/GRANTED/REJECTED 중 하나.
 */
public record ReserveResultResponse(String status) {
}
