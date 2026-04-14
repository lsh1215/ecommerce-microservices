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

    /**
     * 주문 생성 - SAGA Orchestrator에 위임.
     * Phase 0의 동기 try-catch 보상을 이벤트 기반 SAGA로 교체.
     */
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

    /** 주문을 PAID 상태로 전이한다 (Payment 서비스 콜백 시 호출). */
    @Transactional
    public Order markPaid(Long id) {
        Order order = getOrder(id);
        order.markPaid();
        return order;
    }

    /** 주문을 CONFIRMED 상태로 전이한다 (결제 검증 후 호출). */
    @Transactional
    public Order markConfirmed(Long id) {
        Order order = getOrder(id);
        order.markConfirmed();
        return order;
    }

}
