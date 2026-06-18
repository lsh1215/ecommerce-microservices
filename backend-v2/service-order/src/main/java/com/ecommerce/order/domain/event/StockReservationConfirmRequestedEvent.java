package com.ecommerce.order.domain.event;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.event.DomainEvent;
import java.util.List;
import lombok.Getter;

@Getter
public class StockReservationConfirmRequestedEvent extends DomainEvent {

    private Long orderId;
    private String orderNumber;
    private List<ReservationLine> reservations;

    protected StockReservationConfirmRequestedEvent() {
        super();
    }

    public StockReservationConfirmRequestedEvent(Long orderId, String orderNumber,
                                                 List<ReservationLine> reservations) {
        super(KafkaTopics.STOCK_RESERVATION_CONFIRM_REQUESTED);
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.reservations = reservations;
    }

    public record ReservationLine(Long variantId, int quantity) {
    }
}
