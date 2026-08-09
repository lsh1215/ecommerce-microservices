package com.ecommerce.order.application.service;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.CommonErrorCode;
import com.ecommerce.common.outbox.OutboxEvent;
import com.ecommerce.common.outbox.OutboxEventRepository;
import com.ecommerce.order.domain.model.FlashReservation;
import com.ecommerce.order.domain.model.FlashReservationStatus;
import com.ecommerce.order.domain.repository.FlashReservationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선착순 예약 접수/조회/결과적용.
 *
 * <p>{@link #submit}은 예약 레코드(PENDING)와 {@code flash.reserve.requested} outbox 이벤트를 한
 * 트랜잭션에 쓴다(원자성). id(=reservationId)가 공정 순번이고 outbox partitionKey=variantId 라
 * 같은 상품은 한 Kafka 파티션에 도착 순서로 실린다. 실제 발행은 공용 Outbox 폴링 릴레이가 맡는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlashReservationService {

    private final FlashReservationRepository reservationRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public FlashReservation submit(Long customerId, Long variantId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "quantity must be positive");
        }
        FlashReservation reservation = reservationRepository.save(
                FlashReservation.pending(customerId, variantId, quantity));

        outboxRepository.save(OutboxEvent.create(
                "FlashReservation",
                String.valueOf(reservation.getId()),
                KafkaTopics.FLASH_RESERVE_REQUESTED,
                writeRequest(reservation.getId(), variantId, quantity),
                String.valueOf(variantId)));

        return reservation;
    }

    @Transactional(readOnly = true)
    public FlashReservation get(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND,
                        "flash reservation not found: " + reservationId));
    }

    /** granter 결과 적용 — PENDING 에서 한 번만(멱등). 중복/재전송 결과는 무시한다. */
    @Transactional
    public void applyResult(Long reservationId, FlashReservationStatus result) {
        FlashReservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            log.warn("flash.reserve.result for unknown reservationId={}", reservationId);
            return;
        }
        if (!reservation.applyResult(result)) {
            log.debug("flash.reserve.result ignored (already {}) reservationId={}",
                    reservation.getStatus(), reservationId);
        }
    }

    private String writeRequest(long reservationId, long variantId, int quantity) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of("reservationId", reservationId, "variantId", variantId, "quantity", quantity));
        } catch (JsonProcessingException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "cannot serialize flash.reserve.requested payload");
        }
    }
}
