package com.ecommerce.order.domain.event;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.event.DomainEvent;
import java.util.List;
import lombok.Getter;

@Getter
public class StockReservationReleaseRequestedEvent extends DomainEvent {

    private Long orderId;
    private String orderNumber;
    private String reason;
    private List<ReservationLine> reservations;

    protected StockReservationReleaseRequestedEvent() {
        super();
    }

    public StockReservationReleaseRequestedEvent(Long orderId, String orderNumber, String reason,
                                                 List<ReservationLine> reservations) {
        super(KafkaTopics.STOCK_RESERVATION_RELEASE_REQUESTED);
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.reason = reason;
        this.reservations = reservations;
    }

    public record ReservationLine(Long variantId, int quantity) {
    }
}
