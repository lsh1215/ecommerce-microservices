package com.ecommerce.common.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class IdempotentEventHandler {

    private final ProcessedEventRepository processedEventRepository;
    private final boolean idempotencyEnabled;

    public IdempotentEventHandler(
            ProcessedEventRepository processedEventRepository,
            @Value("${application.idempotency.enabled:true}") boolean idempotencyEnabled) {
        this.processedEventRepository = processedEventRepository;
        this.idempotencyEnabled = idempotencyEnabled;
    }

    /**
     * 이벤트를 멱등하게 처리한다.
     * 이미 처리된 eventId면 skip, 아니면 processor 실행 후 처리 완료 기록.
     * DB unique constraint가 동시 처리 시 최종 안전장치 역할을 한다.
     *
     * application.idempotency.enabled=false 면 멱등성 가드를 건너뛰고 processor를 항상 실행한다
     * (Phase 3 Before evidence harness 전용 — 운영에서는 사용 금지).
     */
    @Transactional
    public boolean tryProcess(String eventId, String eventType, Runnable processor) {
        if (!idempotencyEnabled) {
            log.warn("idempotency disabled — running processor without dedup check: eventId={}", eventId);
            processor.run();
            return true;
        }

        if (processedEventRepository.existsByEventId(eventId)) {
            log.info("중복 이벤트 감지, 건너뜀: eventId={}, type={}", eventId, eventType);
            return false;
        }

        processor.run();

        try {
            processedEventRepository.saveAndFlush(ProcessedEvent.of(eventId, eventType));
        } catch (DataIntegrityViolationException e) {
            // 다른 인스턴스가 동시에 같은 이벤트를 처리함
            // processor는 이미 실행됐지만, 비즈니스 로직 자체에 멱등성 체크가 있으므로 안전
            log.info("동시 중복 이벤트 감지 (DB constraint): eventId={}", eventId);
        }
        return true;
    }
}
