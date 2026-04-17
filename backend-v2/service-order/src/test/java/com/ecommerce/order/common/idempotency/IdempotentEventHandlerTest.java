package com.ecommerce.order.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ecommerce.common.idempotency.IdempotentEventHandler;
import com.ecommerce.common.idempotency.ProcessedEvent;
import com.ecommerce.common.idempotency.ProcessedEventRepository;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class IdempotentEventHandlerTest {

    @Mock
    ProcessedEventRepository processedEventRepository;

    private IdempotentEventHandler enabledHandler() {
        return new IdempotentEventHandler(processedEventRepository, true);
    }

    private IdempotentEventHandler disabledHandler() {
        return new IdempotentEventHandler(processedEventRepository, false);
    }

    @Test
    @DisplayName("새 이벤트 처리 시 processor를 실행하고 ProcessedEvent를 저장한다")
    void tryProcess_newEvent_processesAndRecords() {
        AtomicBoolean processorInvoked = new AtomicBoolean(false);
        Runnable processor = () -> processorInvoked.set(true);

        given(processedEventRepository.existsByEventId("evt-1")).willReturn(false);
        given(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .willAnswer(inv -> inv.getArgument(0));

        boolean result = enabledHandler().tryProcess("evt-1", "ORDER_CREATED", processor);

        assertThat(result).isTrue();
        assertThat(processorInvoked).isTrue();
        verify(processedEventRepository).saveAndFlush(
                argThat(e -> e.getEventId().equals("evt-1")
                        && e.getEventType().equals("ORDER_CREATED")));
    }

    @Test
    @DisplayName("이미 처리된 eventId면 processor를 실행하지 않고 false를 반환한다")
    void tryProcess_duplicateEvent_skipsProcessing() {
        AtomicBoolean processorInvoked = new AtomicBoolean(false);
        Runnable processor = () -> processorInvoked.set(true);

        given(processedEventRepository.existsByEventId("evt-1")).willReturn(true);

        boolean result = enabledHandler().tryProcess("evt-1", "ORDER_CREATED", processor);

        assertThat(result).isFalse();
        assertThat(processorInvoked).isFalse();
        verify(processedEventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("processor에서 예외 발생 시 ProcessedEvent를 저장하지 않고 예외를 전파한다")
    void tryProcess_processorThrows_doesNotRecordAsProcessed() {
        given(processedEventRepository.existsByEventId(any())).willReturn(false);
        Runnable failingProcessor = () -> { throw new RuntimeException("processing failed"); };

        assertThatThrownBy(() -> enabledHandler().tryProcess("evt-1", "ORDER_CREATED", failingProcessor))
                .isInstanceOf(RuntimeException.class);
        verify(processedEventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("동시에 같은 이벤트를 처리할 때 DataIntegrityViolationException이 발생해도 true를 반환한다")
    void tryProcess_concurrentDuplicate_handlesGracefully() {
        AtomicBoolean processorInvoked = new AtomicBoolean(false);
        Runnable processor = () -> processorInvoked.set(true);

        given(processedEventRepository.existsByEventId("evt-1")).willReturn(false);
        given(processedEventRepository.saveAndFlush(any()))
                .willThrow(DataIntegrityViolationException.class);

        boolean result = enabledHandler().tryProcess("evt-1", "ORDER_CREATED", processor);

        assertThat(result).isTrue();
        assertThat(processorInvoked).isTrue();
    }

    @Test
    @DisplayName("idempotency=false 일 때 existsByEventId 체크를 건너뛰고 processor를 항상 실행한다")
    void tryProcess_idempotencyDisabled_alwaysRunsProcessorAndSkipsRecord() {
        AtomicBoolean processorInvoked = new AtomicBoolean(false);
        Runnable processor = () -> processorInvoked.set(true);

        boolean result = disabledHandler().tryProcess("evt-1", "ORDER_CREATED", processor);

        assertThat(result).isTrue();
        assertThat(processorInvoked).isTrue();
        verify(processedEventRepository, never()).existsByEventId(any());
        verify(processedEventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("idempotency=false 일 때 동일 eventId를 여러번 주입하면 processor가 매번 실행된다 (중복 허용)")
    void tryProcess_idempotencyDisabled_allowsMultipleInvocationsForSameEventId() {
        AtomicBoolean processorInvoked1 = new AtomicBoolean(false);
        AtomicBoolean processorInvoked2 = new AtomicBoolean(false);

        IdempotentEventHandler handler = disabledHandler();

        handler.tryProcess("evt-dup", "ORDER_CREATED", () -> processorInvoked1.set(true));
        handler.tryProcess("evt-dup", "ORDER_CREATED", () -> processorInvoked2.set(true));

        assertThat(processorInvoked1).isTrue();
        assertThat(processorInvoked2).isTrue();
        verify(processedEventRepository, never()).existsByEventId(any());
    }
}
