package com.ecommerce.product.infra.kafka;

import com.ecommerce.common.config.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 선착순 접수 토픽을 명시적으로 선언한다.
 *
 * <p>지금까지는 브로커의 자동 생성에 맡겼는데, 그러면 파티션 수가 브로커 기본값으로
 * 정해진다. 이 설계에서 파티션 수는 튜닝 값이 아니라 <b>정확성의 조건</b>이다.
 *
 * <ul>
 *   <li>파티션 키가 상품이므로 같은 상품의 접수는 한 파티션에 모이고, 그 안에서
 *       <b>offset이 곧 도착 순번</b>이 된다. 순번을 별도 저장소에 두지 않는 근거가 이것이다.</li>
 *   <li>같은 상품이 여러 파티션에 흩어지면 파티션 간에는 순서가 없으므로 접수 순서가
 *       무너진다. 파티션을 늘려도 상품 하나의 순서는 계속 지켜지는 이유는 키가 상품이기
 *       때문이고, 늘어나는 것은 서로 다른 상품 사이의 병렬성이다.</li>
 * </ul>
 *
 * <p>{@code replicas(3)} / {@code min.insync.replicas=2}는 브로커 하나가 죽어도 확정된
 * 순번이 사라지지 않게 한다. 접수 순번이 유실되면 늦게 온 요청이 앞서는 불공정이
 * 생기는데, 그것이 이 설계가 애초에 피하려던 실패다.
 */
@Configuration
public class FlashTopicConfig {

    /**
     * 파티션 수는 동시에 도는 발매가 서로를 얼마나 기다리는지를 정한다.
     *
     * <p>상품은 키 해시로 파티션에 배정되므로 <b>상품마다 전용 파티션이 배정되지 않는다.</b>
     * 같은 파티션에 걸린 상품들은 한 컨슈머가 차례로 처리하므로 뒤엣것이 앞엣것을 기다린다.
     *
     * <p>동시 발매 N 개를 파티션 P 개에 흩뿌렸을 때 가장 붐비는 파티션의 상품 수(p95, 1,000회
     * 시뮬레이션):
     *
     * <pre>
     *   P=12   N=10 -> 4개   N=20 -> 6개   N=50 -> 10개
     *   P=32   N=10 -> 3개   N=20 -> 4개   N=50 ->  6개
     *   P=64   N=10 -> 2개   N=20 -> 3개   N=50 ->  5개
     *   P=128  N=10 -> 2개   N=20 -> 3개   N=50 ->  4개
     * </pre>
     *
     * <p>64 가 곡선의 무릎이다. 128 로 두 배 늘려도 N=20 에서 같고 N=50 에서만 하나 준다.
     *
     * <p>파티션으로는 바닥을 못 내린다. 겹침이 없어도 상품 하나의 확보 시간(재고 / 확보
     * 처리량)은 그대로다. 그건 배치 확보로 푸는 문제다.
     *
     * <p><b>발매 중에 파티션 수를 바꾸면 안 된다.</b> 늘리는 순간 키 해시의 나머지가 달라져
     * 같은 상품의 뒤 메시지가 다른 파티션으로 가고, 두 파티션 사이에는 순서가 없다.
     */
    @Bean
    public NewTopic flashReserveRequestedTopic() {
        return TopicBuilder.name(KafkaTopics.FLASH_RESERVE_REQUESTED)
                .partitions(64)
                .replicas(3)
                .config("min.insync.replicas", "2")
                .build();
    }

    @Bean
    public NewTopic flashReserveResultTopic() {
        return TopicBuilder.name(KafkaTopics.FLASH_RESERVE_RESULT)
                .partitions(64)
                .replicas(3)
                .config("min.insync.replicas", "2")
                .build();
    }

    /**
     * 매진 신호. 상품당 한 건이라 양이 아주 작다.
     *
     * <p>접수 파드마다 다른 group.id 로 구독하므로 파티션 수는 병렬성과 무관하다. 1로 두면
     * 모든 상품의 신호가 한 파티션에 모여 새 파드가 한 번만 읽고 따라잡을 수 있다.
     *
     * <p>{@code compact} 로 두어 상품당 마지막 상태만 남긴다. 새로 뜬 파드가 처음부터 읽어도
     * 이미 끝난 발매들의 신호를 전부 훑지 않는다.
     */
    @Bean
    public NewTopic flashSaleSoldOutTopic() {
        return TopicBuilder.name(KafkaTopics.FLASH_SALE_SOLD_OUT)
                .partitions(1)
                .replicas(3)
                .config("min.insync.replicas", "2")
                .config("cleanup.policy", "compact")
                .build();
    }
}
