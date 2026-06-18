package com.ecommerce.order.infra.kafka.consumer;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.CommonErrorCode;
import com.ecommerce.common.idempotency.IdempotentEventHandler;
import com.ecommerce.order.application.saga.OrderSagaOrchestrator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductStockEventConsumer {

    private final OrderSagaOrchestrator sagaOrchestrator;
    private final ObjectMapper objectMapper;
    private final IdempotentEventHandler idempotentEventHandler;

    @KafkaListener(
            topics = KafkaTopics.STOCK_RESERVATION_CONFIRMED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void handleStockReservationConfirmed(String message) {
        JsonNode node = parsePayload(message);
        String eventId = node.get("eventId").asText();
        String orderNumber = node.get("orderNumber").asText();

        log.info("stock.reservation.confirmed 이벤트 수신: orderNumber={}", orderNumber);
        idempotentEventHandler.tryProcess(eventId, KafkaTopics.STOCK_RESERVATION_CONFIRMED,
                () -> sagaOrchestrator.handleStockReservationConfirmed(orderNumber));
    }

    @KafkaListener(
            topics = KafkaTopics.STOCK_RESERVATION_RELEASED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void handleStockReservationReleased(String message) {
        JsonNode node = parsePayload(message);
        String eventId = node.get("eventId").asText();
        String orderNumber = node.get("orderNumber").asText();

        log.info("stock.reservation.released 이벤트 수신: orderNumber={}", orderNumber);
        idempotentEventHandler.tryProcess(eventId, KafkaTopics.STOCK_RESERVATION_RELEASED,
                () -> sagaOrchestrator.handleStockReservationReleased(orderNumber));
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
