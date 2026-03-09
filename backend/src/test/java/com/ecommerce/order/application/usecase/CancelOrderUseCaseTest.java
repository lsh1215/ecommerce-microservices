package com.ecommerce.order.application.usecase;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.inventory.application.service.InventoryService;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.model.Orders;
import com.ecommerce.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CancelOrderUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private CancelOrderUseCase cancelOrderUseCase;

    private Orders buildOrderWithItem(String status) {
        Orders order = Orders.create(1L, "key-1", BigDecimal.ZERO, "USD", "addr");
        OrderItem item = OrderItem.create(10L, 2, "Product", "Brand",
                new BigDecimal("50.00"), "USD", "M", "SKU-1");
        order.addItem(item);

        if ("CONFIRMED".equals(status)) {
            order.transitionTo("CONFIRMED");
        }
        return order;
    }

    @Test
    void execute_shouldCancelPendingOrder() {
        Orders order = buildOrderWithItem("PENDING");
        given(orderRepository.findByPublicId("pub-1")).willReturn(Optional.of(order));
        given(orderRepository.save(any(Orders.class))).willAnswer(i -> i.getArgument(0));

        Orders result = cancelOrderUseCase.execute("pub-1", "Customer request");

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        assertThat(result.getStatusHistories()).hasSize(1);
        assertThat(result.getStatusHistories().get(0).getPreviousStatus()).isEqualTo("PENDING");
        assertThat(result.getStatusHistories().get(0).getReason()).isEqualTo("Customer request");
        verify(inventoryService).releaseWithRetry(eq(10L), eq(2), any(), any(), eq("Order cancelled"));
    }

    @Test
    void execute_shouldCancelConfirmedOrder() {
        Orders order = buildOrderWithItem("CONFIRMED");
        given(orderRepository.findByPublicId("pub-2")).willReturn(Optional.of(order));
        given(orderRepository.save(any(Orders.class))).willAnswer(i -> i.getArgument(0));

        Orders result = cancelOrderUseCase.execute("pub-2", null);

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        assertThat(result.getStatusHistories().get(0).getPreviousStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void execute_shouldThrowWhenOrderIsCompleted() {
        Orders order = Orders.create(1L, "key", BigDecimal.ZERO, "USD", "addr");
        order.transitionTo("CONFIRMED");
        order.transitionTo("COMPLETED");
        given(orderRepository.findByPublicId("pub-3")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> cancelOrderUseCase.execute("pub-3", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_CANCELLABLE));

        verify(inventoryService, never()).releaseWithRetry(anyLong(), anyInt(), any(), any(), anyString());
    }

    @Test
    void execute_shouldThrowWhenOrderAlreadyCancelled() {
        Orders order = Orders.create(1L, "key", BigDecimal.ZERO, "USD", "addr");
        order.transitionTo("CANCELLED");
        given(orderRepository.findByPublicId("pub-4")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> cancelOrderUseCase.execute("pub-4", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_CANCELLABLE));
    }

    @Test
    void execute_shouldReleaseInventoryForEachItem() {
        Orders order = Orders.create(1L, "key", BigDecimal.ZERO, "USD", "addr");
        order.addItem(OrderItem.create(10L, 2, "P1", "B1",
                new BigDecimal("50.00"), "USD", "M", "SKU-1"));
        order.addItem(OrderItem.create(20L, 3, "P2", "B2",
                new BigDecimal("30.00"), "USD", "L", "SKU-2"));

        given(orderRepository.findByPublicId("pub-5")).willReturn(Optional.of(order));
        given(orderRepository.save(any(Orders.class))).willAnswer(i -> i.getArgument(0));

        cancelOrderUseCase.execute("pub-5", "Cancelled");

        verify(inventoryService).releaseWithRetry(eq(10L), eq(2), any(), any(), eq("Order cancelled"));
        verify(inventoryService).releaseWithRetry(eq(20L), eq(3), any(), any(), eq("Order cancelled"));
    }
}
