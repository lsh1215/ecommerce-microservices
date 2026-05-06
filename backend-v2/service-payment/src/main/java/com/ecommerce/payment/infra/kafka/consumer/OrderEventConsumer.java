package com.ecommerce.payment.infra.kafka.consumer;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.CommonErrorCode;
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

/**
 * Listeners do <em>not</em> wrap failures in {@code RuntimeException("Failed
 * to process X event", e)}. That hides the original exception type from
 * Spring Kafka's {@link org.springframework.kafka.listener.DefaultErrorHandler}
 * so it can no longer route by type (retry transient vs DLT poison pill).
 * Business exceptions propagate untouched to the error handler.
 *
 * <p>JSON deserialization is the one place we translate: a
 * {@link JsonProcessingException} is a poison pill (retry can't change a
 * malformed payload's outcome), so we surface it as a
 * {@link BusinessException} with {@link CommonErrorCode#INVALID_INPUT}.
 * The error handler is configured to treat {@code BusinessException} as
 * non-retryable (see {@code KafkaConsumerConfig#kafkaErrorHandler}), so
 * this routes straight to {@code <topic>.DLT} instead of churning through
 * 5 retries that will all fail identically.
 */
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
        JsonNode node = parsePayload(message);
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
    public void handleOrderCancelled(String message) {
        JsonNode node = parsePayload(message);
        String eventId = node.get("eventId").asText();
        Long orderId = node.get("orderId").asLong();
        String orderNumber = node.get("orderNumber").asText();

        log.info("order.cancelled 이벤트 수신: orderNumber={}, orderId={}", orderNumber, orderId);
        idempotentEventHandler.tryProcess(eventId, KafkaTopics.ORDER_CANCELLED,
                () -> paymentService.cancelFromEvent(orderId));
    }

    private JsonNode parsePayload(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (JsonProcessingException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "malformed kafka payload: " + e.getOriginalMessage());
        }
    }
}
