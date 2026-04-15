package com.ecommerce.order.infra.kafka.consumer;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.idempotency.IdempotentEventHandler;
import com.ecommerce.order.application.saga.OrderSagaOrchestrator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final OrderSagaOrchestrator sagaOrchestrator;
    private final ObjectMapper objectMapper;
    private final IdempotentEventHandler idempotentEventHandler;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMPLETED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void handlePaymentCompleted(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventId = node.get("eventId").asText();
            String orderNumber = node.get("orderNumber").asText();
            Long orderId = node.get("orderId").asLong();
            Long paymentId = node.get("paymentId").asLong();
            String transactionId = node.get("transactionId").asText();
            BigDecimal amount = new BigDecimal(node.get("amount").asText());

            log.info("payment.completed 이벤트 수신: orderNumber={}, paymentId={}", orderNumber, paymentId);
            idempotentEventHandler.tryProcess(eventId, KafkaTopics.PAYMENT_COMPLETED,
                    () -> sagaOrchestrator.handlePaymentCompleted(orderNumber, orderId, paymentId, transactionId, amount));
        } catch (Exception e) {
            log.error("payment.completed 이벤트 처리 실패: {}", message, e);
            throw new RuntimeException("Failed to process payment.completed event", e);
        }
    }

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_FAILED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void handlePaymentFailed(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventId = node.get("eventId").asText();
            String orderNumber = node.get("orderNumber").asText();
            Long orderId = node.get("orderId").asLong();
            String reason = node.get("reason").asText();

            log.info("payment.failed 이벤트 수신: orderNumber={}, reason={}", orderNumber, reason);
            idempotentEventHandler.tryProcess(eventId, KafkaTopics.PAYMENT_FAILED,
                    () -> sagaOrchestrator.handlePaymentFailed(orderNumber, orderId, reason));
        } catch (Exception e) {
            log.error("payment.failed 이벤트 처리 실패: {}", message, e);
            throw new RuntimeException("Failed to process payment.failed event", e);
        }
    }
}
