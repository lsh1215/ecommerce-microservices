package com.ecommerce.order.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import com.ecommerce.order.application.dto.CreateOrderCommand;
import com.ecommerce.order.application.saga.OrderSagaOrchestrator;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderSagaOrchestrator sagaOrchestrator;

    /** Delegates order creation to the saga orchestrator. */
    @Transactional
    public Order createOrder(CreateOrderCommand command) {
        return sagaOrchestrator.startSaga(command);
    }

    public Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    public Page<Order> getMyOrders(Long customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable);
    }

    @Transactional
    public Order cancelOrder(Long id) {
        Order order = getOrder(id);
        order.cancel();
        return order;
    }

    /** Marks an order as paid. */
    @Transactional
    public Order markPaid(Long id) {
        Order order = getOrder(id);
        order.markPaid();
        return order;
    }

    /** Marks an order as confirmed. */
    @Transactional
    public Order markConfirmed(Long id) {
        Order order = getOrder(id);
        order.markConfirmed();
        return order;
    }

}
