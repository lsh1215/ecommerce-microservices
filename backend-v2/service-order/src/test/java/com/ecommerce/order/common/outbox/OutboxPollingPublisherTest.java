package com.ecommerce.order.common.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ecommerce.common.outbox.OutboxEvent;
import com.ecommerce.common.outbox.OutboxEventRepository;
import com.ecommerce.common.outbox.OutboxEventStatus;
import com.ecommerce.common.outbox.OutboxPollingPublisher;
import com.ecommerce.common.outbox.OutboxRowPublisher;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Outer loop is now thin: scan PENDING rows, dispatch each to
 * {@link OutboxRowPublisher#publishOne(Long)} which owns its own transaction.
 * The Kafka send / markPublished assertions live in OutboxRowPublisher's own
 * test (this module's `service-order` doesn't reach into common's tests, so
 * those assertions are verified at integration time).
 */
@ExtendWith(MockitoExtension.class)
class OutboxPollingPublisherTest {

    @Mock
    OutboxEventRepository outboxRepository;

    @Mock
    OutboxRowPublisher outboxRowPublisher;

    @InjectMocks
    OutboxPollingPublisher publisher;

    @Test
    @DisplayName("미발행 이벤트마다 OutboxRowPublisher.publishOne 을 호출한다")
    void publishPendingEvents_dispatchesEachRowToRowPublisher() throws Exception {
        OutboxEvent event1 = withId(OutboxEvent.create("Order", "1", "order.created", "{}", "ORD-001"), 1L);
        OutboxEvent event2 = withId(OutboxEvent.create("Order", "2", "order.created", "{}", "ORD-002"), 2L);
        given(outboxRepository.findTop100ByStatusOrderByIdAsc(OutboxEventStatus.PENDING))
                .willReturn(List.of(event1, event2));
        doNothing().when(outboxRowPublisher).publishOne(any());

        publisher.publishPendingEvents();

        verify(outboxRowPublisher, times(2)).publishOne(any());
    }

    @Test
    @DisplayName("OptimisticLockException 발생 시 해당 row 는 건너뛰고 다음 row 를 계속 처리한다")
    void publishPendingEvents_optimisticLock_skipsRowAndContinues() throws Exception {
        OutboxEvent event1 = withId(OutboxEvent.create("Order", "1", "order.created", "{}", "ORD-001"), 1L);
        OutboxEvent event2 = withId(OutboxEvent.create("Order", "2", "order.created", "{}", "ORD-002"), 2L);
        given(outboxRepository.findTop100ByStatusOrderByIdAsc(OutboxEventStatus.PENDING))
                .willReturn(List.of(event1, event2));

        // event1 의 publishOne 이 ObjectOptimisticLockingFailureException 을 던짐
        // (다른 인스턴스가 같은 row 의 @Version 을 먼저 올려버린 케이스)
        doThrow(new ObjectOptimisticLockingFailureException("OutboxEvent", event1.getId()))
                .when(outboxRowPublisher).publishOne(eq(event1.getId()));
        doNothing().when(outboxRowPublisher).publishOne(eq(event2.getId()));

        publisher.publishPendingEvents();

        // event1 은 OptLock 으로 skip, event2 는 정상 처리 — 두 row 모두 publishOne 호출됨
        verify(outboxRowPublisher).publishOne(eq(event1.getId()));
        verify(outboxRowPublisher).publishOne(eq(event2.getId()));
        // recordRetryOrMarkFailed 는 OptLock 에서 호출되지 않음 (다른 인스턴스가 publish 성공한 것이라 retry 불필요)
        verify(outboxRowPublisher, never()).recordRetryOrMarkFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("Kafka 전송 실패 등 일반 예외 시 recordRetryOrMarkFailed 호출, 다음 row 도 계속 처리")
    void publishPendingEvents_kafkaSendFails_recordsRetryAndContinues() throws Exception {
        OutboxEvent event1 = withId(OutboxEvent.create("Order", "1", "order.created", "{}", "ORD-001"), 1L);
        OutboxEvent event2 = withId(OutboxEvent.create("Order", "2", "order.created", "{}", "ORD-002"), 2L);
        given(outboxRepository.findTop100ByStatusOrderByIdAsc(OutboxEventStatus.PENDING))
                .willReturn(List.of(event1, event2));

        doThrow(new RuntimeException("Kafka unavailable"))
                .when(outboxRowPublisher).publishOne(eq(event1.getId()));

        publisher.publishPendingEvents();

        verify(outboxRowPublisher).publishOne(eq(event1.getId()));
        verify(outboxRowPublisher).recordRetryOrMarkFailed(eq(event1.getId()), anyString());
        // 새로운 동작: event2 도 publishOne 호출됨 (이전엔 break 했지만 row 별 transaction 분리되어 있어 outer 의 break 불필요)
        verify(outboxRowPublisher).publishOne(eq(event2.getId()));
    }

    @Test
    @DisplayName("recordRetryOrMarkFailed 가 OptimisticLockException 던지면 outer loop 는 swallow 후 다음 row 진행")
    void publishPendingEvents_recordRetryRaceLost_swallowedAndContinues() throws Exception {
        OutboxEvent event = withId(OutboxEvent.create("Order", "1", "order.created", "{}", "ORD-001"), 1L);
        given(outboxRepository.findTop100ByStatusOrderByIdAsc(OutboxEventStatus.PENDING))
                .willReturn(List.of(event));

        doThrow(new RuntimeException("Kafka unavailable"))
                .when(outboxRowPublisher).publishOne(eq(event.getId()));
        doThrow(new ObjectOptimisticLockingFailureException("OutboxEvent", event.getId()))
                .when(outboxRowPublisher).recordRetryOrMarkFailed(eq(event.getId()), anyString());

        // OptLock on retry-count update 가 outer 로 던지지 않아야 함
        publisher.publishPendingEvents();

        verify(outboxRowPublisher).publishOne(eq(event.getId()));
        verify(outboxRowPublisher).recordRetryOrMarkFailed(eq(event.getId()), anyString());
    }

    @Test
    @DisplayName("미발행 이벤트가 없으면 RowPublisher 와 상호작용하지 않는다")
    void publishPendingEvents_emptyQueue_doesNotInvokeRowPublisher() throws Exception {
        given(outboxRepository.findTop100ByStatusOrderByIdAsc(OutboxEventStatus.PENDING))
                .willReturn(Collections.emptyList());

        publisher.publishPendingEvents();

        verify(outboxRowPublisher, never()).publishOne(any());
    }

    /**
     * BaseEntity 의 {@code id} 는 JPA 가 persist 시 채워주는 {@link jakarta.persistence.GeneratedValue}
     * 라 newly-created 객체의 id 는 null 이다. 테스트에서는 publish dispatch 가 id 기반이라
     * reflection 으로 id 를 강제 주입한다.
     */
    private static OutboxEvent withId(OutboxEvent event, Long id) {
        try {
            Field idField = event.getClass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(event, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to set id via reflection", e);
        }
        return event;
    }
}
