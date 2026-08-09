package com.ecommerce.order.api.dto.response;

import com.ecommerce.order.domain.model.FlashReservation;
import com.ecommerce.order.domain.model.FlashReservationStatus;

public record FlashReservationResponse(
        Long reservationId,
        Long variantId,
        int quantity,
        FlashReservationStatus status) {

    public static FlashReservationResponse from(FlashReservation reservation) {
        return new FlashReservationResponse(
                reservation.getId(),
                reservation.getVariantId(),
                reservation.getQuantity(),
                reservation.getStatus());
    }
}
