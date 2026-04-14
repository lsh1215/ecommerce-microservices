package com.ecommerce.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxPollingPublisherTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> stringKafkaTemplate;

    @InjectMocks
    private OutboxPollingPublisher publisher;

    private OutboxEvent sampleEvent;

    @BeforeEach
    void setUp() {
        sampleEvent = OutboxEvent.create(
            "Order", "1", "order.created", "{\"orderId\":1}", "ORDER-001"
        );
    }

    @Test
    void publishPendingEvents_sendsToKafkaAndMarksPublished() {
        given(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
            .willReturn(List.of(sampleEvent));

        RecordMetadata metadata = new RecordMetadata(
            new TopicPartition("order.created", 0), 0, 0, 0, 0, 0);
        SendResult<String, String> sendResult = new SendResult<>(
            new ProducerRecord<>("order.created", "ORDER-001", "{\"orderId\":1}"), metadata);
        given(stringKafkaTemplate.send("order.created", "ORDER-001", "{\"orderId\":1}"))
            .willReturn(CompletableFuture.completedFuture(sendResult));

        publisher.publishPendingEvents();

        assertThat(sampleEvent.isPublished()).isTrue();
    }

    @Test
    void publishPendingEvents_kafkaFailure_stopsProcessingAndIncrementsRetry() {
        OutboxEvent event2 = OutboxEvent.create(
            "Order", "2", "order.created", "{\"orderId\":2}", "ORDER-002"
        );
        given(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
            .willReturn(List.of(sampleEvent, event2));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));
        given(stringKafkaTemplate.send(eq("order.created"), eq("ORDER-001"), anyString()))
            .willReturn(failedFuture);

        publisher.publishPendingEvents();

        assertThat(sampleEvent.isPublished()).isFalse();
        assertThat(sampleEvent.getRetryCount()).isEqualTo(1);
        assertThat(event2.isPublished()).isFalse();
        assertThat(event2.getRetryCount()).isZero();
    }

    @Test
    void publishPendingEvents_noEvents_doesNothing() {
        given(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
            .willReturn(Collections.emptyList());

        publisher.publishPendingEvents();

        verify(stringKafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void publishPendingEvents_maxRetriesExceeded_marksFailed() {
        for (int i = 0; i < 4; i++) {
            sampleEvent.incrementRetryCount();
        }
        assertThat(sampleEvent.getRetryCount()).isEqualTo(4);

        given(outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
            .willReturn(List.of(sampleEvent));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));
        given(stringKafkaTemplate.send(eq("order.created"), eq("ORDER-001"), anyString()))
            .willReturn(failedFuture);

        publisher.publishPendingEvents();

        assertThat(sampleEvent.getRetryCount()).isEqualTo(5);
        assertThat(sampleEvent.isPublished()).isTrue();
    }
}
