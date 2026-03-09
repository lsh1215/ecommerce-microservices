package com.ecommerce.drop.domain.model;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DropEventTest {

    private DropEvent createEvent() {
        return DropEvent.create("Test Drop", "Description",
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 22, 0));
    }

    @Test
    void create_shouldInitializeWithAnnouncedStatus() {
        DropEvent event = createEvent();

        assertThat(event.getTitle()).isEqualTo("Test Drop");
        assertThat(event.getDescription()).isEqualTo("Description");
        assertThat(event.getStatus()).isEqualTo("ANNOUNCED");
        assertThat(event.getStartsAt()).isEqualTo(LocalDateTime.of(2026, 4, 1, 10, 0));
        assertThat(event.getEndsAt()).isEqualTo(LocalDateTime.of(2026, 4, 1, 22, 0));
    }

    @ParameterizedTest
    @CsvSource({
            "ANNOUNCED, OPEN",
            "ANNOUNCED, CLOSED",
            "OPEN, SELLING",
            "SELLING, SOLD_OUT",
            "SELLING, CLOSED",
            "SOLD_OUT, CLOSED"
    })
    void transitionTo_shouldAllowValidTransitions(String from, String to) {
        DropEvent event = createEvent();
        transitionToStatus(event, from);

        event.transitionTo(to);

        assertThat(event.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest
    @CsvSource({
            "ANNOUNCED, SELLING",
            "ANNOUNCED, SOLD_OUT",
            "OPEN, CLOSED",
            "OPEN, ANNOUNCED",
            "SELLING, OPEN",
            "SELLING, ANNOUNCED",
            "SOLD_OUT, OPEN",
            "SOLD_OUT, SELLING",
            "SOLD_OUT, ANNOUNCED",
            "CLOSED, ANNOUNCED",
            "CLOSED, OPEN",
            "CLOSED, SELLING",
            "CLOSED, SOLD_OUT"
    })
    void transitionTo_shouldRejectInvalidTransitions(String from, String to) {
        DropEvent event = createEvent();
        transitionToStatus(event, from);

        assertThatThrownBy(() -> event.transitionTo(to))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
    }

    @Test
    void update_shouldModifyFields() {
        DropEvent event = createEvent();
        LocalDateTime newStart = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDateTime newEnd = LocalDateTime.of(2026, 5, 1, 22, 0);

        event.update("Updated Title", "Updated Desc", newStart, newEnd);

        assertThat(event.getTitle()).isEqualTo("Updated Title");
        assertThat(event.getDescription()).isEqualTo("Updated Desc");
        assertThat(event.getStartsAt()).isEqualTo(newStart);
        assertThat(event.getEndsAt()).isEqualTo(newEnd);
    }

    private void transitionToStatus(DropEvent event, String targetStatus) {
        switch (targetStatus) {
            case "ANNOUNCED" -> {}
            case "OPEN" -> event.transitionTo("OPEN");
            case "SELLING" -> {
                event.transitionTo("OPEN");
                event.transitionTo("SELLING");
            }
            case "SOLD_OUT" -> {
                event.transitionTo("OPEN");
                event.transitionTo("SELLING");
                event.transitionTo("SOLD_OUT");
            }
            case "CLOSED" -> {
                event.transitionTo("OPEN");
                event.transitionTo("SELLING");
                event.transitionTo("CLOSED");
            }
        }
    }
}
