package com.ecommerce.order.infra.kafka.producer;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.order.domain.event.OrderCancelledEvent;
import com.ecommerce.order.domain.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        kafkaTemplate.send(KafkaTopics.ORDER_CREATED, event.getOrderNumber(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("order.created 이벤트 발행 실패: orderNumber={}", event.getOrderNumber(), ex);
                    } else {
                        log.info("order.created 이벤트 발행: orderNumber={}, offset={}",
                                event.getOrderNumber(), result.getRecordMetadata().offset());
                    }
                });
    }

    public void publishOrderCancelled(OrderCancelledEvent event) {
        kafkaTemplate.send(KafkaTopics.ORDER_CANCELLED, event.getOrderNumber(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("order.cancelled 이벤트 발행 실패: orderNumber={}", event.getOrderNumber(), ex);
                    } else {
                        log.info("order.cancelled 이벤트 발행: orderNumber={}, offset={}",
                                event.getOrderNumber(), result.getRecordMetadata().offset());
                    }
                });
    }
}
