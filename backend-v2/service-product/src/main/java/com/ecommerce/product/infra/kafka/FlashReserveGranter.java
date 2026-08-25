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
 * <p><b>처리량 한계.</b> 한 상품의 확보는 파티션 하나를 읽는 컨슈머 하나가 순차로 한다.
 * 이건 공정성의 대가이고 피할 수 없다. 도착 순서를 지키려면 한 줄로 세워야 하고, 한 줄로
 * 세운다는 건 처리도 한 줄이라는 뜻이다. {@code concurrency} 를 올려도 그 상품에는 효과가
 * 없다. 늘어나는 것은 서로 다른 상품 사이의 병렬성뿐이다.
 *
 * <p>실측(3,000 rps 스파이크, 마지막 당첨자가 결과를 알기까지):
 *
 * <pre>
 *   재고    20 ->  약 0.3초 (환산)
 *   재고   100 ->      1.6초
 *   재고 1,000 ->     11.1초
 * </pre>
 *
 * <p>건당 약 11ms 이고 그 대부분이 CPU 가 아니라 DB 왕복과 커밋 대기다(측정 당시 MySQL 6%,
 * 앱 10%). 메시지 하나마다 멱등성 조회 + 유닛 잠금 + UPDATE + 커밋으로 왕복이 네 번 돈다.
 *
 * <p>선착순 한정 판매는 보통 재고를 수십 개 규모로 잡으므로 이 범위에서는 문제가 되지 않는다.
 * 재고가 1,000 을 넘어 통보 지연이 문제가 되면 <b>배치 확보</b>로 푼다. 한 폴에서 받은
 * 레코드들을 파티션별로 나눠 offset 오름차순으로 정렬한 뒤, 상품마다 한 트랜잭션에서
 * {@code LIMIT n FOR UPDATE SKIP LOCKED} 로 n 개를 집어 앞에서부터 짝지으면 된다. 왕복이
 * 배치당 네 번으로 줄어 500건 기준 2,000번이 4번이 된다.
 *
 * <p>배치로 갈 때 새로 지켜야 하는 것 둘. 첫째, 배달 순서를 믿지 말고 offset 으로 명시
 * 정렬해야 한다. 순서를 잃으면 재고가 얼마 안 남은 배치에서 먼저 온 사람이 탈락하고 뒤에
 * 온 사람이 당첨된다. 재고가 넉넉하면 드러나지 않고 매진 경계에서만 나타난다. 둘째,
 * 덜 집어왔을 때 바로 매진 처리하면 안 된다. 리퍼가 쥔 row 를 {@code SKIP LOCKED} 가
 * 건너뛴 것일 수 있으므로, {@code AVAILABLE} 수가 실제로 0 인지 확인하고 판정한다.
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
            containerFactory = "stringKafkaListenerContainerFactory",
            // 컨슈머는 스레드 안전하지 않아서 병렬성을 스레드 풀이 아니라 컨슈머 인스턴스
            // 수로 얻는다. 이 값이 곧 이 리스너가 만드는 컨슈머 수다.
            //
            // 한 상품 안에서는 아무 효과가 없다. 상품은 파티션 하나에 고정되고 그 파티션은
            // 컨슈머 하나가 읽기 때문이다. 효과가 있는 것은 서로 다른 상품이 동시에 팔릴
            // 때이고, 그때 겹치지 않은 상품들이 병렬로 처리된다.
            //
            // 상한이 둘이다. 파티션 수를 넘으면 남는 컨슈머는 놀고, DB 풀을 넘으면 커넥션을
            // 기다리느라 논다. product 풀이 8 이고 리퍼와 다른 리스너가 나눠 쓰므로 4 로 둔다.
            concurrency = "${flash.granter.concurrency:4}"
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
