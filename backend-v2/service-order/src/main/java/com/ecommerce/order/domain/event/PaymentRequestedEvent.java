package com.ecommerce.order.domain.event;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.event.DomainEvent;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class PaymentRequestedEvent extends DomainEvent {

    private Long orderId;
    private String orderNumber;
    private Long customerId;
    private BigDecimal totalAmount;

    protected PaymentRequestedEvent() {
        super();
    }

    public PaymentRequestedEvent(Long orderId, String orderNumber, Long customerId, BigDecimal totalAmount) {
        super(KafkaTopics.PAYMENT_REQUESTED);
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.totalAmount = totalAmount;
    }
}
