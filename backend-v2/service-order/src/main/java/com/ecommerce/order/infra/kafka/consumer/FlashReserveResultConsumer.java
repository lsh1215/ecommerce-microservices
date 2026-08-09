package com.ecommerce.order.infra.kafka.consumer;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.CommonErrorCode;
import com.ecommerce.order.application.service.FlashReservationService;
import com.ecommerce.order.domain.model.FlashReservationStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * granter가 발행한 {@code flash.reserve.result} 를 소비해 예약 상태를 확정한다.
 * GRANTED → RESERVED, 그 외 → SOLD_OUT. 상태 적용은 PENDING 에서 한 번만이라 재전송에도 멱등.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlashReserveResultConsumer {

    private final FlashReservationService flashReservationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.FLASH_RESERVE_RESULT,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void onResult(String message) {
        JsonNode node = parse(message);
        long reservationId = node.get("reservationId").asLong();
        FlashReservationStatus status = "GRANTED".equals(node.get("status").asText())
                ? FlashReservationStatus.RESERVED
                : FlashReservationStatus.SOLD_OUT;
        flashReservationService.applyResult(reservationId, status);
    }

    private JsonNode parse(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (JsonProcessingException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "malformed flash.reserve.result payload: " + e.getOriginalMessage());
        }
    }
}
