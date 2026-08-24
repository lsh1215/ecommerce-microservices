package com.ecommerce.order.api.dto.response;

import com.ecommerce.order.application.dto.FlashSubmitResult;
import com.ecommerce.order.application.service.FlashReservationService.FlashReservationView;
import com.ecommerce.order.domain.model.FlashReservationStatus;

/**
 * 접수 응답과 조회 응답을 겸한다.
 *
 * <p>{@code ticket} 이 사용자에게 주는 식별자이고, 그 안의 {@code partition}/{@code offset} 이
 * 곧 공정 순번이다. 접수 시점에는 DB 에 아무것도 쓰지 않으므로 돌려줄 것이 이것뿐이고,
 * 그것으로 충분하다.
 */
public record FlashReservationResponse(
        String ticket,
        int partition,
        long offset,
        Long variantId,
        int quantity,
        FlashReservationStatus status) {

    public static FlashReservationResponse accepted(FlashSubmitResult result, Long variantId,
                                                    int quantity) {
        return new FlashReservationResponse(result.ticket(), result.partition(), result.offset(),
                variantId, quantity, FlashReservationStatus.PENDING);
    }

    public static FlashReservationResponse from(FlashReservationView view) {
        return new FlashReservationResponse(view.partition() + "-" + view.offset(),
                view.partition(), view.offset(), view.variantId(), view.quantity(), view.status());
    }
}
