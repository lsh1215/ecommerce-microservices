package com.ecommerce.order.domain.event;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.event.DomainEvent;
import lombok.Getter;

@Getter
public class OrderCancelledEvent extends DomainEvent {

    private Long orderId;
    private String orderNumber;
    private String reason;

    protected OrderCancelledEvent() {
        super();
    }

    public OrderCancelledEvent(Long orderId, String orderNumber, String reason) {
        super(KafkaTopics.ORDER_CANCELLED);
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.reason = reason;
    }
}
