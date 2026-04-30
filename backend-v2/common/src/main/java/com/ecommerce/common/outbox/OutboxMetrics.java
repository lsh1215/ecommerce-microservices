package com.ecommerce.common.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox observability — minimum viable surface.
 *
 * <p>What is intentionally <b>not</b> here:
 * <ul>
 *   <li>{@code outbox_events_published_total} counter — duplicates Spring Kafka's
 *       auto-emitted {@code kafka_producer_record_send_total} when {@code MicrometerProducerListener}
 *       is registered on the {@code ProducerFactory}.</li>
 *   <li>{@code outbox_retry_exhaustions_total} counter — single-digit-frequency event;
 *       structured WARN log + LogQL alert is the canonical pattern (Stripe canonical-log-line).</li>
 *   <li>{@code outbox_events_failed} gauge — monotonically increasing, low debugging value.</li>
 * </ul>
 *
 * <p>Publish duration / trace span is emitted by {@link OutboxPollingPublisher}'s
 * {@code Observation.observe()} wrapper directly — no separate Timer needed here.
 *
 * <p>The PENDING gauge is cached via {@link AtomicLong} and refreshed on a 5-second
 * {@code @Scheduled} tick to keep Prometheus scrapes off the DB hot path. A direct
 * {@code countByStatus()} call on every scrape would saturate the
 * {@code outbox_event(status, created_at)} index under load.
 *
 * <p>References:
 * <ul>
 *   <li><a href="https://ridicorp.com/story/transactional-outbox-pattern-ridi/">리디 — Transactional Outbox 패턴</a> (PENDING row count + lag = 2 monitored signals)</li>
 *   <li><a href="https://docs.spring.io/spring-kafka/reference/appendix/micrometer.html">Spring Kafka Micrometer Observation</a> (observation supersedes manual Timer)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class OutboxMetrics {

    private final MeterRegistry meterRegistry;
    private final OutboxEventRepository outboxRepository;

    private final AtomicLong pendingCount = new AtomicLong(0);

    @PostConstruct
    void register() {
        Gauge.builder("outbox_events_pending", pendingCount, AtomicLong::doubleValue)
            .description("Outbox events awaiting publish to Kafka (cached, refreshed every 5s)")
            .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 5000)
    void refreshPendingCount() {
        pendingCount.set(outboxRepository.countByStatus(OutboxEventStatus.PENDING));
    }
}
