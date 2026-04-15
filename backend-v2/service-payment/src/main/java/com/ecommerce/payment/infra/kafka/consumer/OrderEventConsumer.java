package com.ecommerce.payment.infra.kafka.consumer;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.idempotency.IdempotentEventHandler;
import com.ecommerce.payment.application.service.PaymentService;
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
public class OrderEventConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final IdempotentEventHandler idempotentEventHandler;

    @KafkaListener(
            topics = KafkaTopics.ORDER_CREATED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void handleOrderCreated(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventId = node.get("eventId").asText();
            Long orderId = node.get("orderId").asLong();
            String orderNumber = node.get("orderNumber").asText();
            BigDecimal totalAmount = new BigDecimal(node.get("totalAmount").asText());

            log.info("order.created 이벤트 수신: orderNumber={}, orderId={}", orderNumber, orderId);
            idempotentEventHandler.tryProcess(eventId, KafkaTopics.ORDER_CREATED,
                    () -> paymentService.processFromEvent(orderId, orderNumber, totalAmount));
        } catch (Exception e) {
            log.error("order.created 이벤트 처리 실패: {}", message, e);
            throw new RuntimeException("Failed to process order.created event", e);
        }
    }

    @KafkaListener(
            topics = KafkaTopics.ORDER_CANCELLED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void handleOrderCancelled(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventId = node.get("eventId").asText();
            Long orderId = node.get("orderId").asLong();
            String orderNumber = node.get("orderNumber").asText();

            log.info("order.cancelled 이벤트 수신: orderNumber={}, orderId={}", orderNumber, orderId);
            idempotentEventHandler.tryProcess(eventId, KafkaTopics.ORDER_CANCELLED,
                    () -> paymentService.cancelFromEvent(orderId));
        } catch (Exception e) {
            log.error("order.cancelled 이벤트 처리 실패: {}", message, e);
            throw new RuntimeException("Failed to process order.cancelled event", e);
        }
    }
}
