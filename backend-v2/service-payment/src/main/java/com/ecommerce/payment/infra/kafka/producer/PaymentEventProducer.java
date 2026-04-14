package com.ecommerce.payment.infra.kafka.producer;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.payment.domain.event.PaymentCompletedEvent;
import com.ecommerce.payment.domain.event.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        kafkaTemplate.send(KafkaTopics.PAYMENT_COMPLETED, event.getOrderNumber(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("payment.completed 이벤트 발행 실패: orderNumber={}", event.getOrderNumber(), ex);
                    } else {
                        log.info("payment.completed 이벤트 발행: orderNumber={}, paymentId={}, offset={}",
                                event.getOrderNumber(), event.getPaymentId(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        kafkaTemplate.send(KafkaTopics.PAYMENT_FAILED, event.getOrderNumber(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("payment.failed 이벤트 발행 실패: orderNumber={}", event.getOrderNumber(), ex);
                    } else {
                        log.info("payment.failed 이벤트 발행: orderNumber={}, reason={}, offset={}",
                                event.getOrderNumber(), event.getReason(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
