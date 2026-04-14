package com.ecommerce.payment.domain.event;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.event.DomainEvent;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class PaymentCompletedEvent extends DomainEvent {

    private String orderNumber;
    private Long orderId;
    private Long paymentId;
    private String transactionId;
    private BigDecimal amount;

    protected PaymentCompletedEvent() {
        super();
    }

    public PaymentCompletedEvent(String orderNumber, Long orderId, Long paymentId,
                                 String transactionId, BigDecimal amount) {
        super(KafkaTopics.PAYMENT_COMPLETED);
        this.orderNumber = orderNumber;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.transactionId = transactionId;
        this.amount = amount;
    }
}
