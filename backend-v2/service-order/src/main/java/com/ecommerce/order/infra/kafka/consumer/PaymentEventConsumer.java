package com.ecommerce.order.infra.kafka.consumer;

import com.ecommerce.common.config.KafkaTopics;
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

    /**
     * 결제 완료 이벤트를 수신하여 주문 상태를 CONFIRMED -> PAID로 전이한다.
     */
    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMPLETED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void handlePaymentCompleted(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String orderNumber = node.get("orderNumber").asText();
            Long orderId = node.get("orderId").asLong();
            Long paymentId = node.get("paymentId").asLong();
            String transactionId = node.get("transactionId").asText();
            BigDecimal amount = new BigDecimal(node.get("amount").asText());

            log.info("payment.completed 이벤트 수신: orderNumber={}, paymentId={}", orderNumber, paymentId);
            sagaOrchestrator.handlePaymentCompleted(orderNumber, orderId, paymentId, transactionId, amount);
        } catch (Exception e) {
            log.error("payment.completed 이벤트 처리 실패: {}", message, e);
            throw new RuntimeException("Failed to process payment.completed event", e);
        }
    }

    /**
     * 결제 실패 이벤트를 수신하여 보상 트랜잭션을 실행한다 (주문 취소 + 재고 해제).
     */
    @KafkaListener(
            topics = KafkaTopics.PAYMENT_FAILED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void handlePaymentFailed(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String orderNumber = node.get("orderNumber").asText();
            Long orderId = node.get("orderId").asLong();
            String reason = node.get("reason").asText();

            log.info("payment.failed 이벤트 수신: orderNumber={}, reason={}", orderNumber, reason);
            sagaOrchestrator.handlePaymentFailed(orderNumber, orderId, reason);
        } catch (Exception e) {
            log.error("payment.failed 이벤트 처리 실패: {}", message, e);
            throw new RuntimeException("Failed to process payment.failed event", e);
        }
    }
}
