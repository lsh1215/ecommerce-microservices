package com.ecommerce.product.infra.kafka.consumer;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.CommonErrorCode;
import com.ecommerce.common.idempotency.IdempotentEventHandler;
import com.ecommerce.product.application.service.ProductService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockReservationCommandConsumer {

    private final ProductService productService;
    private final ObjectMapper objectMapper;
    private final IdempotentEventHandler idempotentEventHandler;

    @KafkaListener(
            topics = KafkaTopics.STOCK_RESERVATION_CONFIRM_REQUESTED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void handleStockReservationConfirmRequested(String message) {
        JsonNode node = parsePayload(message);
        String eventId = node.get("eventId").asText();
        Long orderId = node.get("orderId").asLong();
        String orderNumber = node.get("orderNumber").asText();
        List<Long> variantIds = variantIds(node);

        log.info("stock.reservation.confirm.requested 이벤트 수신: orderNumber={}", orderNumber);
        idempotentEventHandler.tryProcess(eventId, KafkaTopics.STOCK_RESERVATION_CONFIRM_REQUESTED,
                () -> productService.confirmReservationsAndPublish(orderId, orderNumber, variantIds));
    }

    @KafkaListener(
            topics = KafkaTopics.STOCK_RESERVATION_RELEASE_REQUESTED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void handleStockReservationReleaseRequested(String message) {
        JsonNode node = parsePayload(message);
        String eventId = node.get("eventId").asText();
        Long orderId = node.get("orderId").asLong();
        String orderNumber = node.get("orderNumber").asText();
        List<Long> variantIds = variantIds(node);

        log.info("stock.reservation.release.requested 이벤트 수신: orderNumber={}", orderNumber);
        idempotentEventHandler.tryProcess(eventId, KafkaTopics.STOCK_RESERVATION_RELEASE_REQUESTED,
                () -> productService.releaseReservationsAndPublish(orderId, orderNumber, variantIds));
    }

    private List<Long> variantIds(JsonNode node) {
        List<Long> variantIds = new ArrayList<>();
        node.withArray("reservations").forEach(item -> variantIds.add(item.get("variantId").asLong()));
        return variantIds;
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
