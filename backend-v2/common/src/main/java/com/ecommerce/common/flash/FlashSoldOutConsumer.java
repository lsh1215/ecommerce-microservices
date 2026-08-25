package com.ecommerce.common.flash;

import com.ecommerce.common.config.KafkaTopics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 매진 신호를 받아 이 파드의 로컬 플래그를 세운다.
 *
 * <p>접수(Order)와 확보(Product) 양쪽이 모두 구독한다. Order 는 매진 뒤 요청을 발행하지
 * 않으려고, Product 는 재발매 신호를 받아 로컬 플래그를 풀려고 필요하다. Product 가
 * 구독하지 않으면 파드가 여럿일 때 재발매 호출을 받은 한 파드만 플래그가 풀린다.
 *
 * <p>{@code groupId} 에 랜덤 접미사를 붙이는 것이 핵심이다. 파드들이 같은 그룹이면
 * 파티션이 나뉘어 <b>한 파드만</b> 신호를 받고 나머지는 계속 Kafka 에 발행한다. 컨슈머
 * 그룹은 부하 분산 장치이지 브로드캐스트 장치가 아니다.
 *
 * <p>그룹이 파드마다 다르므로 offset 도 파드마다 따로 관리된다. {@code earliest} 로 읽어
 * 새로 뜬 파드도 이미 매진된 상품을 알게 한다. 신호가 상품당 한 건이라 양이 작다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlashSoldOutConsumer {

    private final SoldOutRegistry soldOutRegistry;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.FLASH_SALE_SOLD_OUT,
            groupId = "#{'flash-sold-out-' + T(java.util.UUID).randomUUID()}",
            properties = "auto.offset.reset=earliest",
            containerFactory = "stringKafkaListenerContainerFactory")
    public void onSoldOut(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            long variantId = node.get("variantId").asLong();
            if (node.path("soldOut").asBoolean(true)) {
                soldOutRegistry.markSoldOut(variantId);
                log.info("flash sold out variantId={}", variantId);
            } else {
                soldOutRegistry.clear(variantId);
                log.info("flash sale reopened variantId={}", variantId);
            }
        } catch (Exception e) {
            log.error("malformed flash.sale.sold-out: {}", message, e);
        }
    }
}
