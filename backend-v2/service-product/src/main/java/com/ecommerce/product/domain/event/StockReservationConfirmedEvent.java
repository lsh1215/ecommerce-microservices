package com.ecommerce.product.domain.event;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.event.DomainEvent;
import lombok.Getter;

@Getter
public class StockReservationConfirmedEvent extends DomainEvent {

    private Long orderId;
    private String orderNumber;

    protected StockReservationConfirmedEvent() {
        super();
    }

    public StockReservationConfirmedEvent(Long orderId, String orderNumber) {
        super(KafkaTopics.STOCK_RESERVATION_CONFIRMED);
        this.orderId = orderId;
        this.orderNumber = orderNumber;
    }
}
