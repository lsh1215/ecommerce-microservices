package com.ecommerce.payment.infra.kafka.consumer;

import com.ecommerce.common.config.KafkaTopics;
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

    /**
     * 주문 생성 이벤트를 수신하여 비동기 결제를 처리한다.
     * Payment 서비스가 다운되었다가 복구되면 Kafka에 쌓인 메시지를 순차 처리한다.
     */
    @KafkaListener(
            topics = KafkaTopics.ORDER_CREATED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void handleOrderCreated(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            Long orderId = node.get("orderId").asLong();
            String orderNumber = node.get("orderNumber").asText();
            BigDecimal totalAmount = new BigDecimal(node.get("totalAmount").asText());

            log.info("order.created 이벤트 수신: orderNumber={}, orderId={}", orderNumber, orderId);
            paymentService.processFromEvent(orderId, orderNumber, totalAmount);
        } catch (Exception e) {
            log.error("order.created 이벤트 처리 실패: {}", message, e);
            throw new RuntimeException("Failed to process order.created event", e);
        }
    }

    /**
     * 주문 취소 이벤트를 수신하여 결제를 취소하거나 환불 처리한다.
     */
    @KafkaListener(
            topics = KafkaTopics.ORDER_CANCELLED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void handleOrderCancelled(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            Long orderId = node.get("orderId").asLong();
            String orderNumber = node.get("orderNumber").asText();

            log.info("order.cancelled 이벤트 수신: orderNumber={}, orderId={}", orderNumber, orderId);
            paymentService.cancelFromEvent(orderId);
        } catch (Exception e) {
            log.error("order.cancelled 이벤트 처리 실패: {}", message, e);
            throw new RuntimeException("Failed to process order.cancelled event", e);
        }
    }
}
