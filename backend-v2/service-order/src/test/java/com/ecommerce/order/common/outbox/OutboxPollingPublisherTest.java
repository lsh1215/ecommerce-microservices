package com.ecommerce.order.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ecommerce.common.outbox.OutboxEvent;
import com.ecommerce.common.outbox.OutboxEventRepository;
import com.ecommerce.common.outbox.OutboxEventStatus;
import com.ecommerce.common.outbox.OutboxPollingPublisher;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class OutboxPollingPublisherTest {

    @Mock
    OutboxEventRepository outboxRepository;

    @Mock
    KafkaTemplate<String, String> stringKafkaTemplate;

    @InjectMocks
    OutboxPollingPublisher publisher;

    @Test
    @DisplayName("미발행 이벤트가 있으면 Kafka로 전송하고 markPublished를 호출한다")
    void publishPendingEvents_singleEvent_publishesSuccessfullyAndMarksPublished() {
        // Given
        OutboxEvent event = OutboxEvent.create("Order", "1", "order.created", "{}", "ORD-001");
        given(outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .willReturn(List.of(event));

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future =
                CompletableFuture.completedFuture(mock(SendResult.class));
        given(stringKafkaTemplate.send(anyString(), anyString(), anyString())).willReturn(future);

        // When
        publisher.publishPendingEvents();

        // Then
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.isPublished()).isTrue();
        assertThat(event.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Kafka 전송 실패 시 retryCount를 1 증가시키고 다음 이벤트는 처리하지 않는다")
    void publishPendingEvents_kafkaSendFails_incrementsRetryCountAndBreaks() {
        // Given
        OutboxEvent event1 = OutboxEvent.create("Order", "1", "order.created", "{}", "ORD-001");
        OutboxEvent event2 = OutboxEvent.create("Order", "2", "order.created", "{}", "ORD-002");
        given(outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .willReturn(List.of(event1, event2));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));
        given(stringKafkaTemplate.send(anyString(), anyString(), anyString())).willReturn(failedFuture);

        // When
        publisher.publishPendingEvents();

        // Then
        assertThat(event1.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event1.isPublished()).isFalse();
        assertThat(event1.getRetryCount()).isEqualTo(1);
        verify(stringKafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("최대 재시도 횟수 초과 시 이벤트를 FAILED로 표시하고 다음 이벤트를 처리한다")
    void publishPendingEvents_maxRetriesExceeded_marksEventFailed() {
        // Given
        OutboxEvent event = OutboxEvent.create("Order", "1", "order.created", "{}", "ORD-001");
        for (int i = 0; i < 4; i++) {
            event.incrementRetryCount();
        }
        given(outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .willReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));
        given(stringKafkaTemplate.send(anyString(), anyString(), anyString())).willReturn(failedFuture);

        // When
        publisher.publishPendingEvents();

        // Then
        assertThat(event.getRetryCount()).isEqualTo(5);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.isPublished()).isFalse();
    }

    @Test
    @DisplayName("OptimisticLockException 발생 시 해당 이벤트를 건너뛰고 다음 이벤트를 처리한다")
    void publishPendingEvents_optimisticLockException_skipsEventAndContinues() {
        // Given
        OutboxEvent event1 = OutboxEvent.create("Order", "1", "order.created", "{}", "ORD-001");
        OutboxEvent event2 = OutboxEvent.create("Order", "2", "order.created", "{}", "ORD-002");
        given(outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .willReturn(List.of(event1, event2));

        given(stringKafkaTemplate.send(eq("order.created"), eq("ORD-001"), anyString()))
                .willThrow(new ObjectOptimisticLockingFailureException("OutboxEvent", "1"));

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> successFuture =
                CompletableFuture.completedFuture(mock(SendResult.class));
        given(stringKafkaTemplate.send(eq("order.created"), eq("ORD-002"), anyString()))
                .willReturn(successFuture);

        // When
        publisher.publishPendingEvents();

        // Then
        assertThat(event1.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event1.isPublished()).isFalse();
        assertThat(event2.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event2.isPublished()).isTrue();
    }

    @Test
    @DisplayName("미발행 이벤트가 없으면 Kafka와 상호작용하지 않는다")
    void publishPendingEvents_emptyQueue_doesNotInteractWithKafka() {
        // Given
        given(outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .willReturn(Collections.emptyList());

        // When
        publisher.publishPendingEvents();

        // Then
        verify(stringKafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }
}
