package com.ecommerce.product.domain.event;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.event.DomainEvent;
import lombok.Getter;

@Getter
public class StockReservationReleasedEvent extends DomainEvent {

    private Long orderId;
    private String orderNumber;

    protected StockReservationReleasedEvent() {
        super();
    }

    public StockReservationReleasedEvent(Long orderId, String orderNumber) {
        super(KafkaTopics.STOCK_RESERVATION_RELEASED);
        this.orderId = orderId;
        this.orderNumber = orderNumber;
    }
}
