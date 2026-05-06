package com.ecommerce.payment.infra.kafka.consumer;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.idempotency.IdempotentEventHandler;
import com.ecommerce.payment.application.service.PaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Listener methods declare `throws JsonProcessingException` and let any
// other exception propagate naturally. Spring Kafka's DefaultErrorHandler
// then decides retry vs DLT based on the exception type and the configured
// BackOff. Wrapping every failure in `new RuntimeException("Failed to ...", e)`
// (the previous shape) just hid the real exception type from the error
// handler and from observability — the original cause is preserved by
// propagation, the listener's role is to translate the message and
// dispatch to the application layer.
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
    public void handleOrderCreated(String message) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(message);
        String eventId = node.get("eventId").asText();
        Long orderId = node.get("orderId").asLong();
        String orderNumber = node.get("orderNumber").asText();
        BigDecimal totalAmount = new BigDecimal(node.get("totalAmount").asText());

        log.info("order.created 이벤트 수신: orderNumber={}, orderId={}", orderNumber, orderId);
        idempotentEventHandler.tryProcess(eventId, KafkaTopics.ORDER_CREATED,
                () -> paymentService.processFromEvent(orderId, orderNumber, totalAmount));
    }

    @KafkaListener(
            topics = KafkaTopics.ORDER_CANCELLED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void handleOrderCancelled(String message) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(message);
        String eventId = node.get("eventId").asText();
        Long orderId = node.get("orderId").asLong();
        String orderNumber = node.get("orderNumber").asText();

        log.info("order.cancelled 이벤트 수신: orderNumber={}, orderId={}", orderNumber, orderId);
        idempotentEventHandler.tryProcess(eventId, KafkaTopics.ORDER_CANCELLED,
                () -> paymentService.cancelFromEvent(orderId));
    }
}
