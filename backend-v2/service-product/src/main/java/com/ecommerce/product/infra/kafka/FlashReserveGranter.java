package com.ecommerce.product.infra.kafka;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.CommonErrorCode;
import com.ecommerce.common.flash.SoldOutRegistry;
import com.ecommerce.product.application.service.FlashReserveService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 선착순 확보 granter.
 *
 * <p>파티션(=variantId)당 컨슈머 하나가 offset 순서대로 처리한다. 그 순서가 곧 공정 순번이다.
 *
 * <p>세 가지가 이 클래스의 요점이다.
 *
 * <ol>
 *   <li><b>탈락 경로는 아무것도 쓰지 않는다.</b> 실패 결과를 기록하거나 발행하면, 그 수가
 *       재고와 무관하게 늘어나 접수에서 DB 쓰기를 없앤 의미가 사라진다. 이 프로젝트의
 *       아웃박스 릴레이가 실측 초당 53건이었던 것이, 메시지당 쓰기가 붙으면 어떻게 되는지
 *       보여주는 사례다.</li>
 *   <li><b>매진되면 한 번만 알린다.</b> 신호를 받은 접수 파드들이 그 뒤 요청을 Kafka 에
 *       발행조차 하지 않으므로, 뒤늦게 온 요청이 토픽에 쌓이지 않는다. 큐는 스파이크를
 *       기다리게 만들어서 흡수하므로, 받아들인 건수가 곧 사용자의 대기 시간이다.</li>
 *   <li><b>결과는 성공만 발행한다.</b> 탈락은 접수 측에서 "승자 row 없음 + 매진"으로
 *       판정한다.</li>
 * </ol>
 *
 * <p>Outbox 를 쓰지 않는다. 확보(DB)와 결과 발행(Kafka)이 원자적이지 않으므로 확보 뒤
 * 발행 전에 죽으면 결과가 유실될 수 있는데, 그때는 유닛의 TTL 리퍼가 회수한다. 결과 유실이
 * 오버셀로 이어지지 않는 이유는 재고 상한이 유닛 row 수로 정해져 있기 때문이다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlashReserveGranter {

    private final FlashReserveService flashReserveService;
    private final SoldOutRegistry soldOutRegistry;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.FLASH_RESERVE_REQUESTED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void onReserveRequested(ConsumerRecord<String, String> record) {
        JsonNode node = parse(record.value());
        long variantId = node.get("variantId").asLong();

        // 이미 매진이면 offset 만 넘긴다. DB 도 Kafka 도 건드리지 않는다.
        // 이 경로가 무작업이라야 남은 메시지를 초당 수만 건으로 비울 수 있다.
        if (soldOutRegistry.isSoldOut(variantId)) {
            return;
        }

        long customerId = node.get("customerId").asLong();
        int quantity = node.get("quantity").asInt();

        // 확보 주체 식별자로 offset 을 쓴다. 같은 상품은 같은 파티션이므로
        // (variantId, offset) 이 유일하고, 재전송돼도 같은 값이라 이중 확보가 없다.
        boolean granted = flashReserveService.reserve(record.offset(), variantId, quantity);

        if (!granted) {
            // 처음 소진을 관측한 컨슈머만 신호를 낸다.
            if (soldOutRegistry.markSoldOut(variantId)) {
                publishSoldOut(variantId);
            }
            return;
        }

        kafkaTemplate.send(KafkaTopics.FLASH_RESERVE_RESULT, String.valueOf(variantId),
                Map.of("partition", record.partition(),
                        "offset", record.offset(),
                        "customerId", customerId,
                        "variantId", variantId,
                        "quantity", quantity));
    }

    private void publishSoldOut(long variantId) {
        kafkaTemplate.send(KafkaTopics.FLASH_SALE_SOLD_OUT, String.valueOf(variantId),
                Map.of("variantId", variantId, "soldOut", true));
        log.info("flash sold out variantId={}", variantId);
    }

    private JsonNode parse(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (JsonProcessingException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "malformed flash.reserve.requested payload: " + e.getOriginalMessage());
        }
    }
}
