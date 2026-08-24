package com.ecommerce.order.infra.kafka.consumer;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.order.application.service.FlashReservationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 확보 성공 결과를 받아 승자 row 를 남긴다.
 *
 * <p>실패 결과는 오지 않는다. granter 가 발행하지 않기 때문이다. 탈락자마다 이벤트를 만들면
 * 그 수가 재고와 무관하게 늘어나, 접수에서 DB 쓰기를 없앤 의미가 사라진다.
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
            containerFactory = "kafkaListenerContainerFactory")
    public void onResult(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            flashReservationService.recordGranted(
                    node.get("partition").asInt(),
                    node.get("offset").asLong(),
                    node.get("customerId").asLong(),
                    node.get("variantId").asLong(),
                    node.get("quantity").asInt());
        } catch (Exception e) {
            log.error("malformed flash.reserve.result: {}", message, e);
        }
    }
}
