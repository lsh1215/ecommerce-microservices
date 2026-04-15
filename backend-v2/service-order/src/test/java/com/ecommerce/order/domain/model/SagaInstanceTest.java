package com.ecommerce.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class SagaInstanceTest {

    @Test
    @DisplayName("SagaInstance 생성 시 초기 상태는 ORDER_CREATED이다")
    void create_initialState_isOrderCreated() {
        // When
        SagaInstance saga = SagaInstance.create(1L, "ORD-001");

        // Then
        assertThat(saga.getState()).isEqualTo(SagaState.ORDER_CREATED);
        assertThat(saga.getOrderId()).isEqualTo(1L);
        assertThat(saga.getOrderNumber()).isEqualTo("ORD-001");
        assertThat(saga.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("ORDER_CREATED 상태에서 PAYMENT_PROCESSING으로 전이 성공")
    void moveToPaymentProcessing_fromOrderCreated_succeeds() {
        // Given
        SagaInstance saga = SagaInstance.create(1L, "ORD-001");

        // When
        saga.moveToPaymentProcessing();

        // Then
        assertThat(saga.getState()).isEqualTo(SagaState.PAYMENT_PROCESSING);
    }

    @Test
    @DisplayName("PAYMENT_PROCESSING 상태에서 COMPLETED로 전이 성공")
    void moveToCompleted_fromPaymentProcessing_succeeds() {
        // Given
        SagaInstance saga = buildSagaInState(SagaState.PAYMENT_PROCESSING);

        // When
        saga.moveToCompleted();

        // Then
        assertThat(saga.getState()).isEqualTo(SagaState.COMPLETED);
    }

    @Test
    @DisplayName("PAYMENT_PROCESSING 상태에서 COMPENSATING으로 전이 성공")
    void moveToCompensating_fromPaymentProcessing_succeeds() {
        // Given
        SagaInstance saga = buildSagaInState(SagaState.PAYMENT_PROCESSING);

        // When
        saga.moveToCompensating();

        // Then
        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSATING);
    }

    @Test
    @DisplayName("COMPENSATING 상태에서 COMPENSATED로 전이 성공")
    void moveToCompensated_fromCompensating_succeeds() {
        // Given
        SagaInstance saga = buildSagaInState(SagaState.COMPENSATING);

        // When
        saga.moveToCompensated();

        // Then
        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSATED);
    }

    @ParameterizedTest
    @EnumSource(SagaState.class)
    @DisplayName("어떤 상태에서도 FAILED로 전이하고 failureReason을 설정한다")
    void moveToFailed_fromAnyState_setsFailureReason(SagaState startingState) {
        // Given
        SagaInstance saga = buildSagaInState(startingState);

        // When
        saga.moveToFailed("timeout");

        // Then
        assertThat(saga.getState()).isEqualTo(SagaState.FAILED);
        assertThat(saga.getFailureReason()).isEqualTo("timeout");
    }

    @ParameterizedTest(name = "{0} 상태에서 {1} 전이 시도 -> 예외")
    @MethodSource("invalidTransitions")
    @DisplayName("허용되지 않는 상태 전이 시 IllegalStateException을 던진다")
    void invalidTransition_throws(SagaState startingState, String transitionMethod) {
        // Given
        SagaInstance saga = buildSagaInState(startingState);

        // When / Then
        assertThatThrownBy(() -> invokeTransition(saga, transitionMethod))
                .isInstanceOf(IllegalStateException.class);
    }

    static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                Arguments.of(SagaState.ORDER_CREATED, "moveToCompleted"),
                Arguments.of(SagaState.ORDER_CREATED, "moveToCompensating"),
                Arguments.of(SagaState.ORDER_CREATED, "moveToCompensated"),
                Arguments.of(SagaState.PAYMENT_PROCESSING, "moveToPaymentProcessing"),
                Arguments.of(SagaState.COMPLETED, "moveToPaymentProcessing"),
                Arguments.of(SagaState.COMPLETED, "moveToCompensating"),
                Arguments.of(SagaState.COMPENSATED, "moveToPaymentProcessing"),
                Arguments.of(SagaState.COMPENSATED, "moveToCompleted"),
                Arguments.of(SagaState.COMPENSATED, "moveToCompensating"),
                Arguments.of(SagaState.FAILED, "moveToPaymentProcessing")
        );
    }

    private void invokeTransition(SagaInstance saga, String method) {
        switch (method) {
            case "moveToPaymentProcessing" -> saga.moveToPaymentProcessing();
            case "moveToCompleted" -> saga.moveToCompleted();
            case "moveToCompensating" -> saga.moveToCompensating();
            case "moveToCompensated" -> saga.moveToCompensated();
            default -> throw new IllegalArgumentException("Unknown transition: " + method);
        }
    }

    private SagaInstance buildSagaInState(SagaState target) {
        SagaInstance saga = SagaInstance.create(1L, "ORD-001");
        return switch (target) {
            case ORDER_CREATED -> saga;
            case PAYMENT_PROCESSING -> {
                saga.moveToPaymentProcessing();
                yield saga;
            }
            case COMPLETED -> {
                saga.moveToPaymentProcessing();
                saga.moveToCompleted();
                yield saga;
            }
            case COMPENSATING -> {
                saga.moveToPaymentProcessing();
                saga.moveToCompensating();
                yield saga;
            }
            case COMPENSATED -> {
                saga.moveToPaymentProcessing();
                saga.moveToCompensating();
                saga.moveToCompensated();
                yield saga;
            }
            case FAILED -> {
                saga.moveToFailed("pre-existing");
                yield saga;
            }
        };
    }
}
