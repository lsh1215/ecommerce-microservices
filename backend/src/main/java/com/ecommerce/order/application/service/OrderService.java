package com.ecommerce.order.application.service;

import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.model.Orders;
import com.ecommerce.order.domain.repository.OrderItemRepository;
import com.ecommerce.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional(readOnly = true)
    public Orders getByPublicId(String publicId) {
        return orderRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Order", publicId));
    }

    @Transactional(readOnly = true)
    public Page<Orders> listByCustomerId(Long customerId, Pageable pageable) {
        Page<Orders> page = orderRepository.findByCustomerId(customerId, pageable);
        page.getContent().forEach(order -> Hibernate.initialize(order.getItems()));
        return page;
    }

    @Transactional(readOnly = true)
    public List<OrderItem> getOrderItems(Long orderId) {
        return orderItemRepository.findByOrderId(orderId);
    }
}
