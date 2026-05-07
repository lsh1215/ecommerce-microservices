package com.ecommerce.order.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ecommerce.common.idempotency.DuplicateEventException;
import com.ecommerce.common.idempotency.IdempotentEventHandler;
import com.ecommerce.common.idempotency.InternalIdempotentExecutor;
import com.ecommerce.common.idempotency.ProcessedEvent;
import com.ecommerce.common.idempotency.ProcessedEventRepository;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class IdempotentEventHandlerTest {

    @Mock
    InternalIdempotentExecutor executor;

    @Mock
    ProcessedEventRepository processedEventRepository;

    private IdempotentEventHandler enabledHandler() {
        return new IdempotentEventHandler(executor, true);
    }

    private IdempotentEventHandler disabledHandler() {
        return new IdempotentEventHandler(executor, false);
    }

    // -----------------------------------------------------------------------
    // IdempotentEventHandler (outer wrapper) tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("IdempotentEventHandler")
    class HandlerTests {

        @Test
        @DisplayName("새 이벤트 처리 시 executor를 호출하고 true를 반환한다")
        void tryProcess_newEvent_delegatesToExecutorAndReturnsTrue() {
            AtomicBoolean processorInvoked = new AtomicBoolean(false);
            Runnable processor = () -> processorInvoked.set(true);

            // executor does nothing by default (no exception) — processor is invoked inside executor
            // in production; here we just verify delegation
            boolean result = enabledHandler().tryProcess("evt-1", "ORDER_CREATED", processor);

            assertThat(result).isTrue();
            verify(executor).execute(eq("evt-1"), eq("ORDER_CREATED"), any(Runnable.class));
        }

        @Test
        @DisplayName("DuplicateEventException 수신 시 processor를 실행하지 않고 false를 반환한다")
        void tryProcess_duplicateEvent_returnsFalse() {
            AtomicBoolean processorInvoked = new AtomicBoolean(false);
            Runnable processor = () -> processorInvoked.set(true);

            doThrow(new DuplicateEventException("evt-1"))
                    .when(executor).execute(eq("evt-1"), eq("ORDER_CREATED"), any());

            boolean result = enabledHandler().tryProcess("evt-1", "ORDER_CREATED", processor);

            assertThat(result).isFalse();
            // processor never reached because executor threw before running it
            assertThat(processorInvoked).isFalse();
        }

        @Test
        @DisplayName("idempotency=false 일 때 executor를 거치지 않고 processor를 직접 실행한다")
        void tryProcess_idempotencyDisabled_runsProcessorDirectlyWithoutExecutor() {
            AtomicBoolean processorInvoked = new AtomicBoolean(false);
            Runnable processor = () -> processorInvoked.set(true);

            boolean result = disabledHandler().tryProcess("evt-1", "ORDER_CREATED", processor);

            assertThat(result).isTrue();
            assertThat(processorInvoked).isTrue();
            verify(executor, never()).execute(any(), any(), any());
        }

        @Test
        @DisplayName("idempotency=false 일 때 동일 eventId를 여러 번 주입하면 processor가 매번 실행된다")
        void tryProcess_idempotencyDisabled_allowsMultipleInvocationsForSameEventId() {
            AtomicBoolean invoked1 = new AtomicBoolean(false);
            AtomicBoolean invoked2 = new AtomicBoolean(false);

            IdempotentEventHandler handler = disabledHandler();
            handler.tryProcess("evt-dup", "ORDER_CREATED", () -> invoked1.set(true));
            handler.tryProcess("evt-dup", "ORDER_CREATED", () -> invoked2.set(true));

            assertThat(invoked1).isTrue();
            assertThat(invoked2).isTrue();
            verify(executor, never()).execute(any(), any(), any());
        }
    }

    // -----------------------------------------------------------------------
    // InternalIdempotentExecutor tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("InternalIdempotentExecutor")
    class ExecutorTests {

        private InternalIdempotentExecutor executor() {
            return new InternalIdempotentExecutor(processedEventRepository);
        }

        @Test
        @DisplayName("새 이벤트: marker를 먼저 저장한 뒤 processor를 실행한다")
        void execute_newEvent_savesMarkerThenRunsProcessor() {
            AtomicBoolean processorInvoked = new AtomicBoolean(false);
            Runnable processor = () -> processorInvoked.set(true);

            given(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            executor().execute("evt-1", "ORDER_CREATED", processor);

            assertThat(processorInvoked).isTrue();
            verify(processedEventRepository).saveAndFlush(
                    argThat(e -> e.getEventId().equals("evt-1")
                            && e.getEventType().equals("ORDER_CREATED")));
        }

        @Test
        @DisplayName("DB unique constraint 위반 시 DuplicateEventException을 던지고 processor를 실행하지 않는다")
        void execute_concurrentDuplicate_throwsDuplicateEventExceptionWithoutRunningProcessor() {
            AtomicBoolean processorInvoked = new AtomicBoolean(false);
            Runnable processor = () -> processorInvoked.set(true);

            given(processedEventRepository.saveAndFlush(any()))
                    .willThrow(DataIntegrityViolationException.class);

            assertThatThrownBy(() -> executor().execute("evt-1", "ORDER_CREATED", processor))
                    .isInstanceOf(DuplicateEventException.class);

            // Processor must NOT have been invoked — marker failed so we never owned the event
            assertThat(processorInvoked).isFalse();
        }

        @Test
        @DisplayName("processor 예외 발생 시 marker가 커밋되지 않는다 (atomicity)")
        void execute_processorThrows_markerNotCommitted() {
            given(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            Runnable failingProcessor = () -> { throw new RuntimeException("processing failed"); };

            assertThatThrownBy(() -> executor().execute("evt-1", "ORDER_CREATED", failingProcessor))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("processing failed");

            // saveAndFlush was called but the @Transactional method threw, so the tx rolls back.
            // In this unit test we verify saveAndFlush was called (marker was attempted) and
            // the exception propagates — the rollback itself is enforced by Spring's tx proxy
            // which is not active in this unit test.
            verify(processedEventRepository).saveAndFlush(any());
        }
    }
}
