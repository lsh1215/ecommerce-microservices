package com.ecommerce.order.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import com.ecommerce.order.application.dto.CreateOrderCommand;
import com.ecommerce.order.application.dto.OrderDetailResult;
import com.ecommerce.order.application.saga.OrderSagaOrchestrator;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.repository.OrderRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderSagaOrchestrator sagaOrchestrator;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderDetailResult createOrder(CreateOrderCommand command) {
        return OrderDetailResult.from(sagaOrchestrator.startSaga(command));
    }

    public OrderDetailResult getOrder(Long id) {
        return OrderDetailResult.from(findDetail(id));
    }

    public Page<OrderDetailResult> getMyOrders(Long customerId, Pageable pageable) {
        Page<Order> page = orderRepository.findByCustomerId(customerId, pageable);
        List<Long> ids = page.getContent().stream()
                .map(Order::getId)
                .toList();
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, page.getTotalElements());
        }

        Map<Long, Order> detailsById = orderRepository.findDetailsByIdIn(ids).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        List<OrderDetailResult> content = ids.stream()
                .map(detailsById::get)
                .map(OrderDetailResult::from)
                .toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @Transactional
    public OrderDetailResult cancelOrder(Long id) {
        Order order = findDetail(id);
        order.cancel();
        return OrderDetailResult.from(order);
    }

    @Transactional
    public OrderDetailResult markPaid(Long id) {
        Order order = findDetail(id);
        order.markPaid();
        return OrderDetailResult.from(order);
    }

    @Transactional
    public OrderDetailResult markConfirmed(Long id) {
        Order order = findDetail(id);
        order.markConfirmed();
        return OrderDetailResult.from(order);
    }

    private Order findDetail(Long id) {
        return orderRepository.findDetailById(id)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }
}
