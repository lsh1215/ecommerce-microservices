package com.ecommerce.order.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ecommerce.common.outbox.OutboxEvent;
import com.ecommerce.common.outbox.OutboxEventRepository;
import com.ecommerce.common.outbox.OutboxEventStatus;
import com.ecommerce.common.outbox.OutboxRowPublisher;
import io.micrometer.observation.ObservationRegistry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Unit tests for the per-row publisher that owns {@code REQUIRES_NEW} on
 * each outbox publish. {@link OutboxRowPublisher} re-loads the row inside
 * its own transaction (so {@code markPublished} dirty-tracks correctly)
 * and short-circuits when the row was already published by a peer poller.
 *
 * <p>The {@code @Transactional(REQUIRES_NEW)} boundary is a Spring
 * runtime concern and is verified by integration tests; this unit test
 * focuses on the in-method logic — the re-load, the status guard, the
 * Kafka send, and the markPublished call.
 */
@ExtendWith(MockitoExtension.class)
class OutboxRowPublisherTest {

    @Mock
    OutboxEventRepository outboxRepository;

    @Mock
    KafkaTemplate<String, String> stringKafkaTemplate;

    OutboxRowPublisher publisher;

    @BeforeEach
    void setUp() {
        // ObservationRegistry.NOOP runs the lambda passed to observeChecked
        // but emits no metrics or spans — the publish logic still executes,
        // the observation hooks just do nothing.
        publisher = new OutboxRowPublisher(outboxRepository, stringKafkaTemplate, ObservationRegistry.NOOP);
    }

    @Test
    @DisplayName("PENDING 상태 row 는 Kafka 로 전송하고 markPublished 를 호출한다")
    void publishOne_pendingRow_sendsAndMarksPublished() throws Exception {
        OutboxEvent event = OutboxEvent.create("Order", "1", "order.created", "{\"k\":\"v\"}", "ORD-001");
        given(outboxRepository.findById(1L)).willReturn(Optional.of(event));

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future =
                CompletableFuture.completedFuture(mock(SendResult.class));
        given(stringKafkaTemplate.send(eq("order.created"), eq("ORD-001"), eq("{\"k\":\"v\"}")))
                .willReturn(future);

        publisher.publishOne(1L);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.isPublished()).isTrue();
        verify(stringKafkaTemplate).send(eq("order.created"), eq("ORD-001"), eq("{\"k\":\"v\"}"));
    }

    @Test
    @DisplayName("이미 PUBLISHED 상태인 row 는 Kafka 전송 없이 즉시 반환한다 (다른 인스턴스가 먼저 publish 한 케이스)")
    void publishOne_alreadyPublishedRow_skipsKafkaSend() throws Exception {
        OutboxEvent event = OutboxEvent.create("Order", "1", "order.created", "{}", "ORD-001");
        event.markPublished();
        given(outboxRepository.findById(1L)).willReturn(Optional.of(event));

        publisher.publishOne(1L);

        verify(stringKafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("row 가 사라진 경우 (retention cleanup) 조용히 반환한다")
    void publishOne_missingRow_returnsSilently() throws Exception {
        given(outboxRepository.findById(99L)).willReturn(Optional.empty());

        publisher.publishOne(99L);

        verify(stringKafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Kafka 전송 실패 시 exception 을 propagate 하고 row status 는 PENDING 으로 유지된다")
    void publishOne_kafkaSendFails_propagatesAndKeepsPending() {
        OutboxEvent event = OutboxEvent.create("Order", "1", "order.created", "{}", "ORD-001");
        given(outboxRepository.findById(1L)).willReturn(Optional.of(event));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka unavailable"));
        given(stringKafkaTemplate.send(anyString(), anyString(), anyString())).willReturn(failed);

        assertThatThrownBy(() -> publisher.publishOne(1L))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Kafka unavailable");
        // markPublished 는 호출되지 않아야 함 → row 는 PENDING 유지
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.isPublished()).isFalse();
    }

    @Test
    @DisplayName("recordRetryOrMarkFailed: MAX_RETRIES 미만이면 retryCount 만 증가시키고 status 는 PENDING 유지")
    void recordRetry_belowMaxRetries_incrementsCountKeepsPending() {
        OutboxEvent event = OutboxEvent.create("Order", "1", "order.created", "{}", "ORD-001");
        given(outboxRepository.findById(1L)).willReturn(Optional.of(event));

        publisher.recordRetryOrMarkFailed(1L, "Kafka timeout");

        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    @DisplayName("recordRetryOrMarkFailed: MAX_RETRIES 도달 시 status 를 FAILED 로 마킹")
    void recordRetry_atMaxRetries_marksFailed() {
        OutboxEvent event = OutboxEvent.create("Order", "1", "order.created", "{}", "ORD-001");
        for (int i = 0; i < 4; i++) {
            event.incrementRetryCount();
        }
        given(outboxRepository.findById(1L)).willReturn(Optional.of(event));

        publisher.recordRetryOrMarkFailed(1L, "still down");

        assertThat(event.getRetryCount()).isEqualTo(5);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.isPublished()).isFalse();
    }

    @Test
    @DisplayName("recordRetryOrMarkFailed: row 가 사라진 경우 (retention cleanup) 조용히 반환")
    void recordRetry_missingRow_returnsSilently() {
        given(outboxRepository.findById(99L)).willReturn(Optional.empty());

        publisher.recordRetryOrMarkFailed(99L, "any error");
        // No exception, no DB write.
    }
}
