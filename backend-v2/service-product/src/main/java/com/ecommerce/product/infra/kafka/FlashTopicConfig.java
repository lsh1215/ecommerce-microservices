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

    @Bean
    public NewTopic flashReserveRequestedTopic() {
        return TopicBuilder.name(KafkaTopics.FLASH_RESERVE_REQUESTED)
                .partitions(12)
                .replicas(3)
                .config("min.insync.replicas", "2")
                .build();
    }

    @Bean
    public NewTopic flashReserveResultTopic() {
        return TopicBuilder.name(KafkaTopics.FLASH_RESERVE_RESULT)
                .partitions(12)
                .replicas(3)
                .config("min.insync.replicas", "2")
                .build();
    }
}
