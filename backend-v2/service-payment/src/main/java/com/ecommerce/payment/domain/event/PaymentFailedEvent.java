package com.ecommerce.payment.domain.event;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.event.DomainEvent;
import lombok.Getter;

@Getter
public class PaymentFailedEvent extends DomainEvent {

    private String orderNumber;
    private Long orderId;
    private String reason;

    protected PaymentFailedEvent() {
        super();
    }

    public PaymentFailedEvent(String orderNumber, Long orderId, String reason) {
        super(KafkaTopics.PAYMENT_FAILED);
        this.orderNumber = orderNumber;
        this.orderId = orderId;
        this.reason = reason;
    }
}
