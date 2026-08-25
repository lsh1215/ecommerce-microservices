package com.ecommerce.product.infra.kafka;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.CommonErrorCode;
import com.ecommerce.common.flash.SoldOutRegistry;
import com.ecommerce.product.application.service.FlashReserveService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
 * <p>이 수치는 결과 발행을 기다리지 않던 코드에서 잰 값이다. 아래에 적은 대로 지금은 ack 를
 * 기다리므로 당첨자마다 브로커 왕복이 하나 더 붙는다. 재측정은 하지 못했다.
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
 * <p><b>확보(DB)와 결과 발행(Kafka)은 여기서 원자적이지 않다.</b> 그래서 dual write 지만
 * Outbox 를 쓰지 않는다. Outbox 가 필요한 것은 <b>입력이 재생 불가능할 때</b>다. HTTP 요청은
 * 날아가면 끝이라 DB 만 커밋되고 발행이 실패하면 복구할 근거가 없고, 그래서 발행할 사실을
 * DB 에 같이 적어둔다. 여기서는 입력이 이미 Kafka 레코드라 재생이 공짜다.
 * {@code enable.auto.commit=false} 이므로 리스너가 끝나야 offset 이 커밋되고, 그 전에 죽으면
 * 같은 레코드가 재전송된다. 재전송되면 {@code reserve()} 의 멱등성 조회가 이미 확보한 유닛을
 * 찾아 결과를 다시 발행한다. Outbox 테이블이 하던 일을 컨슈머 offset 이 그대로 한다.
 *
 * <p>그래서 <b>발행은 반드시 ack 를 받고 반환해야 한다.</b> 기다리지 않으면 리스너가 먼저
 * 끝나 offset 이 커밋되고 결과는 프로듀서 버퍼에만 남는데, 그 순간 죽으면 재전송도 없고
 * 결과도 없다. 유닛만 RESERVED 로 남아 리퍼가 회수하고, 당첨된 사람은 조용히 탈락한다.
 * 재고가 이미 매진 처리됐다면 회수된 유닛은 팔리지도 않는다(언더셀). 기다리는 비용은
 * 당첨자 수만큼의 왕복이고, 그건 요청 수가 아니라 <b>재고 수</b>에 묶여 있다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlashReserveGranter {

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(3);

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
        //
        // 발행 실패로 재전송된 당첨 레코드가 여기서 버려질 걱정은 없다. 한 상품은 파티션
        // 하나에 고정되고 그 파티션은 컨슈머 하나가 읽으므로, 그 상품의 매진 표시를 세우는
        // 것도 같은 스레드다. 재전송을 처리하는 동안 그 스레드는 다음 레코드로 넘어가지
        // 못하니 표시가 먼저 설 수 없다.
        if (soldOutRegistry.isSoldOut(variantId)) {
            return;
        }

        long customerId = node.get("customerId").asLong();
        int quantity = node.get("quantity").asInt();

        // 확보 주체 식별자로 offset 을 쓴다. 같은 상품은 같은 파티션이므로
        // (variantId, offset) 이 유일하고, 재전송돼도 같은 값이라 이중 확보가 없다.
        boolean granted = flashReserveService.reserve(record.offset(), variantId, quantity);

        if (!granted) {
            markAndBroadcastSoldOut(variantId);
            return;
        }

        // ack 를 기다린다. 실패하면 예외가 올라가 컨테이너가 같은 레코드를 재전송하고,
        // reserve() 의 멱등성이 이미 확보한 유닛을 찾아 결과를 다시 발행한다.
        send(KafkaTopics.FLASH_RESERVE_RESULT, variantId,
                Map.of("partition", record.partition(),
                        "offset", record.offset(),
                        "customerId", customerId,
                        "variantId", variantId,
                        "quantity", quantity));
    }

    private void markAndBroadcastSoldOut(long variantId) {
        if (!soldOutRegistry.markSoldOut(variantId)) {
            return;
        }
        try {
            send(KafkaTopics.FLASH_SALE_SOLD_OUT, variantId,
                    Map.of("variantId", variantId, "soldOut", true));
            log.info("flash sold out variantId={}", variantId);
        } catch (RuntimeException e) {
            // 신호를 못 보냈으면 표시도 되돌린다. 그대로 두면 이 파드만 매진을 알고 다른
            // 파드는 계속 접수하는데, markSoldOut 이 이미 true 를 소비해서 다시 알릴
            // 기회가 없다. 되돌리면 바로 뒤에 오는 탈락 레코드가 다시 시도한다.
            //
            // 예외로 올리지 않는 이유는 정확성이 걸려 있지 않기 때문이다. 재고 판정은
            // DB 가 하므로 신호가 늦어도 오버셀이 나지 않고, 늦어지는 것은 거절 속도뿐이다.
            // 올리면 남은 메시지를 비우는 경로가 재시도 백오프 동안 막힌다.
            soldOutRegistry.clear(variantId);
            log.error("failed to broadcast sold out variantId={}", variantId, e);
        }
    }

    private void send(String topic, long variantId, Map<String, Object> payload) {
        try {
            kafkaTemplate.send(topic, String.valueOf(variantId), payload)
                    .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while publishing " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            // IllegalStateException 은 재시도 대상이다(KafkaConsumerConfig 의 notRetryable
            // 목록에 없다). 백오프 재시도 뒤에도 실패하면 DLT 로 가고, 그 유닛은 리퍼가
            // 회수한다. 조용히 사라지는 대신 DLT 레코드가 남는다.
            throw new IllegalStateException(
                    "failed to publish " + topic + " variantId=" + variantId, e);
        }
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
