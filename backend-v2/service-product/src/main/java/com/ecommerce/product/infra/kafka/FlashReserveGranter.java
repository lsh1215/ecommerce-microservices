package com.ecommerce.product.infra.kafka;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.CommonErrorCode;
import com.ecommerce.common.outbox.OutboxEvent;
import com.ecommerce.common.outbox.OutboxEventRepository;
import com.ecommerce.product.application.service.FlashReserveService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선착순 예약 granter — {@code flash.reserve.requested} 를 파티션(=variantId) 순서대로 소비해
 * {@code SELECT ... FOR UPDATE SKIP LOCKED} 로 유닛을 확보하고, 결과를 outbox
 * ({@code flash.reserve.result})로 무유실 발행한다.
 *
 * <p>파티션당 단일 컨슈머라 한 상품 안에서는 도착(offset) 순서대로 직렬 처리 = 공정성. 상품이 다르면
 * 파티션이 달라 병렬 = 수평 확장. 확보와 결과 이벤트가 한 트랜잭션이라 grant 됐는데 결과가 유실되는
 * 일이 없고, 재전송돼도 유닛 확보는 orderId 기준 멱등이라 이중 확보가 없다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlashReserveGranter {

    private final FlashReserveService flashReserveService;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.FLASH_RESERVE_REQUESTED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void onReserveRequested(String message) {
        JsonNode node = parse(message);
        long reservationId = node.get("reservationId").asLong();
        long variantId = node.get("variantId").asLong();
        int quantity = node.get("quantity").asInt();

        boolean granted = flashReserveService.reserve(reservationId, variantId, quantity);
        String status = granted ? "GRANTED" : "SOLD_OUT";

        outboxRepository.save(OutboxEvent.create(
                "FlashReserve",
                String.valueOf(reservationId),
                KafkaTopics.FLASH_RESERVE_RESULT,
                writeResult(reservationId, variantId, status),
                String.valueOf(reservationId)));

        log.debug("flash reserve {} reservationId={} variantId={}", status, reservationId, variantId);
    }

    private JsonNode parse(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (JsonProcessingException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "malformed flash.reserve.requested payload: " + e.getOriginalMessage());
        }
    }

    private String writeResult(long reservationId, long variantId, String status) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of("reservationId", reservationId, "variantId", variantId, "status", status));
        } catch (JsonProcessingException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "cannot serialize flash.reserve.result payload");
        }
    }
}
