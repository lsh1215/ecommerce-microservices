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
    @DisplayName("SagaInstance 생성 시 초기 상태는 STOCK_RESERVATION_PENDING이다")
    void create_initialState_isStockReservationPending() {
        // 실행
        SagaInstance saga = SagaInstance.create(1L, "ORD-001");

        // 검증
        assertThat(saga.getState()).isEqualTo(SagaState.STOCK_RESERVATION_PENDING);
        assertThat(saga.getOrderId()).isEqualTo(1L);
        assertThat(saga.getOrderNumber()).isEqualTo("ORD-001");
        assertThat(saga.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("STOCK_RESERVATION_PENDING 상태에서 STOCK_RESERVED로 전이 성공")
    void moveToStockReserved_fromPending_succeeds() {
        // 준비
        SagaInstance saga = SagaInstance.create(1L, "ORD-001");

        // 실행
        saga.moveToStockReserved();

        // 검증
        assertThat(saga.getState()).isEqualTo(SagaState.STOCK_RESERVED);
    }

    @Test
    @DisplayName("STOCK_RESERVED 상태에서 PAYMENT_PROCESSING으로 전이 성공")
    void moveToPaymentProcessing_fromStockReserved_succeeds() {
        // 준비
        SagaInstance saga = buildSagaInState(SagaState.STOCK_RESERVED);

        // 실행
        saga.moveToPaymentProcessing();

        // 검증
        assertThat(saga.getState()).isEqualTo(SagaState.PAYMENT_PROCESSING);
    }

    @Test
    @DisplayName("재고 예약 실패 상태로 전이하며 failureReason을 설정한다")
    void moveToStockReservationFailed_setsFailureReason() {
        // 준비
        SagaInstance saga = SagaInstance.create(1L, "ORD-001");

        // 실행
        saga.moveToStockReservationFailed("variant=200 failed");

        // 검증
        assertThat(saga.getState()).isEqualTo(SagaState.STOCK_RESERVATION_FAILED);
        assertThat(saga.getFailureReason()).isEqualTo("variant=200 failed");
    }

    @Test
    @DisplayName("COMPENSATING 상태에서 COMPENSATION_RETRY_REQUIRED로 전이하며 failureReason을 설정한다")
    void moveToCompensationRetryRequired_fromCompensating_setsFailureReason() {
        // 준비
        SagaInstance saga = buildSagaInState(SagaState.COMPENSATING);

        // 실행
        saga.moveToCompensationRetryRequired("release failed");

        // 검증
        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSATION_RETRY_REQUIRED);
        assertThat(saga.getFailureReason()).isEqualTo("release failed");
    }

    @Test
    @DisplayName("PAYMENT_PROCESSING 상태에서 COMPLETED로 전이 성공")
    void moveToCompleted_fromPaymentProcessing_succeeds() {
        // 준비
        SagaInstance saga = buildSagaInState(SagaState.PAYMENT_PROCESSING);

        // 실행
        saga.moveToCompleted();

        // 검증
        assertThat(saga.getState()).isEqualTo(SagaState.COMPLETED);
    }

    @Test
    @DisplayName("PAYMENT_PROCESSING 상태에서 COMPENSATING으로 전이 성공")
    void moveToCompensating_fromPaymentProcessing_succeeds() {
        // 준비
        SagaInstance saga = buildSagaInState(SagaState.PAYMENT_PROCESSING);

        // 실행
        saga.moveToCompensating();

        // 검증
        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSATING);
    }

    @Test
    @DisplayName("COMPENSATING 상태에서 COMPENSATED로 전이 성공")
    void moveToCompensated_fromCompensating_succeeds() {
        // 준비
        SagaInstance saga = buildSagaInState(SagaState.COMPENSATING);

        // 실행
        saga.moveToCompensated();

        // 검증
        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSATED);
    }

    @ParameterizedTest
    @EnumSource(SagaState.class)
    @DisplayName("어떤 상태에서도 FAILED로 전이하고 failureReason을 설정한다")
    void moveToFailed_fromAnyState_setsFailureReason(SagaState startingState) {
        // 준비
        SagaInstance saga = buildSagaInState(startingState);

        // 실행
        saga.moveToFailed("timeout");

        // 검증
        assertThat(saga.getState()).isEqualTo(SagaState.FAILED);
        assertThat(saga.getFailureReason()).isEqualTo("timeout");
    }

    @ParameterizedTest(name = "{0} 상태에서 {1} 전이 시도 -> 예외")
    @MethodSource("invalidTransitions")
    @DisplayName("허용되지 않는 상태 전이 시 IllegalStateException을 던진다")
    void invalidTransition_throws(SagaState startingState, String transitionMethod) {
        // 준비
        SagaInstance saga = buildSagaInState(startingState);

        // 실행 및 검증
        assertThatThrownBy(() -> invokeTransition(saga, transitionMethod))
                .isInstanceOf(IllegalStateException.class);
    }

    static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                Arguments.of(SagaState.STOCK_RESERVATION_PENDING, "moveToCompleted"),
                Arguments.of(SagaState.STOCK_RESERVATION_PENDING, "moveToCompensating"),
                Arguments.of(SagaState.STOCK_RESERVATION_PENDING, "moveToCompensated"),
                Arguments.of(SagaState.PAYMENT_PROCESSING, "moveToPaymentProcessing"),
                Arguments.of(SagaState.COMPLETED, "moveToPaymentProcessing"),
                Arguments.of(SagaState.COMPLETED, "moveToCompensating"),
                Arguments.of(SagaState.COMPENSATED, "moveToPaymentProcessing"),
                Arguments.of(SagaState.COMPENSATED, "moveToCompleted"),
                Arguments.of(SagaState.COMPENSATED, "moveToCompensating"),
                Arguments.of(SagaState.FAILED, "moveToPaymentProcessing"),
                Arguments.of(SagaState.STOCK_RESERVATION_FAILED, "moveToPaymentProcessing"),
                Arguments.of(SagaState.COMPENSATION_RETRY_REQUIRED, "moveToCompensated")
        );
    }

    private void invokeTransition(SagaInstance saga, String method) {
        switch (method) {
            case "moveToPaymentProcessing" -> saga.moveToPaymentProcessing();
            case "moveToStockReserved" -> saga.moveToStockReserved();
            case "moveToCompleted" -> saga.moveToCompleted();
            case "moveToCompensating" -> saga.moveToCompensating();
            case "moveToCompensated" -> saga.moveToCompensated();
            default -> throw new IllegalArgumentException("Unknown transition: " + method);
        }
    }

    private SagaInstance buildSagaInState(SagaState target) {
        SagaInstance saga = SagaInstance.create(1L, "ORD-001");
        return switch (target) {
            case STOCK_RESERVATION_PENDING -> saga;
            case STOCK_RESERVED -> {
                saga.moveToStockReserved();
                yield saga;
            }
            case PAYMENT_PROCESSING -> {
                saga.moveToStockReserved();
                saga.moveToPaymentProcessing();
                yield saga;
            }
            case COMPLETED -> {
                saga.moveToStockReserved();
                saga.moveToPaymentProcessing();
                saga.moveToCompleted();
                yield saga;
            }
            case COMPENSATING -> {
                saga.moveToStockReserved();
                saga.moveToPaymentProcessing();
                saga.moveToCompensating();
                yield saga;
            }
            case COMPENSATED -> {
                saga.moveToStockReserved();
                saga.moveToPaymentProcessing();
                saga.moveToCompensating();
                saga.moveToCompensated();
                yield saga;
            }
            case STOCK_RESERVATION_FAILED -> {
                saga.moveToStockReservationFailed("pre-existing");
                yield saga;
            }
            case COMPENSATION_RETRY_REQUIRED -> {
                saga.moveToStockReserved();
                saga.moveToPaymentProcessing();
                saga.moveToCompensating();
                saga.moveToCompensationRetryRequired("pre-existing");
                yield saga;
            }
            case FAILED -> {
                saga.moveToFailed("pre-existing");
                yield saga;
            }
        };
    }
}
