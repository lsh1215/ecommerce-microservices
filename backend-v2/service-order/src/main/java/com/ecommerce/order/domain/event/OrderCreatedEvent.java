package com.ecommerce.order.domain.event;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.event.DomainEvent;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class OrderCreatedEvent extends DomainEvent {

    private Long orderId;
    private String orderNumber;
    private Long customerId;
    private BigDecimal totalAmount;

    protected OrderCreatedEvent() {
        super();
    }

    public OrderCreatedEvent(Long orderId, String orderNumber, Long customerId, BigDecimal totalAmount) {
        super(KafkaTopics.ORDER_CREATED);
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.totalAmount = totalAmount;
    }
}
