package com.ecommerce.order.application.service;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.CommonErrorCode;
import com.ecommerce.common.flash.SoldOutRegistry;
import com.ecommerce.order.OrderErrorCode;
import com.ecommerce.order.application.dto.FlashSubmitResult;
import com.ecommerce.order.domain.model.FlashReservation;
import com.ecommerce.order.domain.model.FlashReservationStatus;
import com.ecommerce.order.domain.repository.FlashReservationRepository;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선착순 접수.
 *
 * <p>접수는 <b>Kafka 발행만</b> 한다. DB 에 쓰지 않는다.
 *
 * <p>이전에는 예약 row 와 outbox row 를 한 트랜잭션에 쓰고 릴레이가 발행했다. Outbox 는
 * "DB 쓰기와 Kafka 발행을 원자적으로 묶는" 문제를 푸는 도구인데, <b>접수에서 DB 에 쓰지
 * 않으면 그 문제가 애초에 없다.</b> 그런데도 Outbox 를 쓰면 접수당 DB 쓰기가 2회가 되어,
 * 스파이크를 DB 에서 떼어놓으려던 목적과 정반대로 간다.
 *
 * <p>공정 순번도 바뀐다. 예전에는 예약 row 의 auto-increment id 였는데, 그 값은 커넥션 풀을
 * 통과한 순서로 정해진다. HikariCP 의 대기는 FIFO 가 아니고(스레드 로컬 캐시), 하필 승자가
 * 갈리는 순간이 풀이 가장 막힌 순간이다. 지금은 <b>파티션 offset</b>이 순번이다. 순서가
 * 막히기 전에 한 지점에서 정해지고, 그 뒤의 혼잡이 이미 정해진 순서를 바꾸지 못한다.
 *
 * <p><b>공정성 기준.</b> 이 시스템이 지키기로 한 문장은 하나다.
 *
 * <blockquote>같은 상품의 접수는 Kafka 파티션에 append 된 순서대로 재고를 배정한다.
 * 그 순서는 브로커가 정하고 이후 바뀌지 않는다.</blockquote>
 *
 * <p>약속하지 않는 것도 같이 적는다. 클릭 순서가 아니다. 네트워크 지연, 로드밸런서,
 * 클라이언트 재시도가 그 앞에 있다. 접수 파드 간 발행 시점 편차(밀리초)도 보정하지 않는다.
 *
 * <p>기준을 세웠으므로 깨지는 지점마다 장치가 있다.
 *
 * <ul>
 *   <li>프로듀서 재시도로 순서가 뒤집히는 것 — {@code enable.idempotence=true}. 브로커가
 *       시퀀스를 검사해 순서가 어긋난 배치를 거부하므로, in-flight 를 5 로 두고도 순서가
 *       지켜진다. 이 설정을 끄면 {@code max.in.flight=1} 로 내려야 한다.</li>
 *   <li>재전송으로 같은 사람에게 두 번 배정되는 것 — offset 을 유닛의 주인으로 기록한다.</li>
 *   <li>발매 중 파티션 수 변경 — 금지. 키 해시의 나머지가 달라져 같은 상품의 뒤 메시지가
 *       다른 파티션으로 가고, 파티션 사이에는 순서가 없다.</li>
 * </ul>
 *
 * <p>이 기준은 검증 가능하다. 승자의 offset 이 그 파티션의 첫 N 개와 연속이어야 하고,
 * 구멍이 있으면 먼저 온 요청이 밀린 것이다. {@code scripts/loadtest/flash-verify.py} 가
 * 그것을 본다.
 *
 * <p>발행 실패는 접수 실패로 돌려준다. 삼키고 성공을 반환하면 순번을 받지 못한 요청이
 * 접수된 것처럼 보인다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlashReservationService {

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(3);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final FlashReservationRepository reservationRepository;
    private final SoldOutRegistry soldOutRegistry;

    public FlashSubmitResult submit(Long customerId, Long variantId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "quantity must be positive");
        }
        // 매진 뒤에 온 요청은 발행조차 하지 않는다. 토픽에 쌓이지 않으므로 지울 일도 없고,
        // 앞서 접수된 사람들의 결과 통보가 그만큼 빨라진다.
        if (soldOutRegistry.isSoldOut(variantId)) {
            throw new BusinessException(OrderErrorCode.FLASH_SOLD_OUT,
                    "sold out: variantId=" + variantId);
        }

        // 페이로드에 식별자를 넣지 않는다. 브로커가 붙이는 offset 이 식별자다.
        Map<String, Object> payload = Map.of(
                "customerId", customerId,
                "variantId", variantId,
                "quantity", quantity);

        try {
            // 파티션 키가 variantId 라 같은 상품은 한 파티션에 도착 순서로 실린다.
            SendResult<String, Object> result = kafkaTemplate
                    .send(KafkaTopics.FLASH_RESERVE_REQUESTED, String.valueOf(variantId), payload)
                    .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return new FlashSubmitResult(
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "flash submit interrupted");
        } catch (ExecutionException | TimeoutException e) {
            log.warn("flash submit publish failed variantId={}: {}", variantId, e.toString());
            throw new BusinessException(OrderErrorCode.FLASH_SUBMIT_FAILED,
                    "could not accept the request; retry");
        }
    }

    /**
     * 조회. 승자만 row 로 남기므로 세 상태를 이렇게 가른다.
     *
     * <ul>
     *   <li>row 있음 → 확보 성공</li>
     *   <li>row 없고 매진 → 탈락</li>
     *   <li>row 없고 매진 아님 → 아직 처리 전</li>
     * </ul>
     *
     * <p>탈락자마다 row 를 남기지 않는 이유는 그 수가 재고와 무관하게 늘기 때문이다. 재고
     * 100 개짜리에서 탈락 17만 건을 기록하면, 접수에서 DB 쓰기를 없앤 의미가 사라진다.
     */
    @Transactional(readOnly = true)
    public FlashReservationView get(int partition, long offset, Long variantId) {
        return reservationRepository.findByPartitionNoAndRecordOffset(partition, offset)
                .map(r -> new FlashReservationView(partition, offset, r.getVariantId(),
                        r.getQuantity(), FlashReservationStatus.RESERVED))
                .orElseGet(() -> new FlashReservationView(partition, offset, variantId, 0,
                        variantId != null && soldOutRegistry.isSoldOut(variantId)
                                ? FlashReservationStatus.SOLD_OUT
                                : FlashReservationStatus.PENDING));
    }

    /** granter 가 확보에 성공한 건만 기록한다. 재전송돼도 offset 이 같아 한 번만 남는다. */
    @Transactional
    public void recordGranted(int partition, long offset, Long customerId, Long variantId,
                              int quantity) {
        if (reservationRepository.findByPartitionNoAndRecordOffset(partition, offset).isPresent()) {
            return;
        }
        reservationRepository.save(
                FlashReservation.granted(partition, offset, customerId, variantId, quantity));
    }

    public record FlashReservationView(int partition, long offset, Long variantId, int quantity,
                                       FlashReservationStatus status) {
    }
}
