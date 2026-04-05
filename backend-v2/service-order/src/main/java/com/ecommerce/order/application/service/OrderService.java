package com.ecommerce.order.application.service;

import com.ecommerce.order.api.dto.request.CreateOrderRequest;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.repository.OrderItemRepository;
import com.ecommerce.order.domain.repository.OrderRepository;
import com.ecommerce.order.domain.service.CustomerDirectoryPort;
import com.ecommerce.order.domain.service.ProductCatalogPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ObjectProvider<ProductCatalogPort> productCatalogProvider;
    private final ObjectProvider<CustomerDirectoryPort> customerDirectoryProvider;

    public Order createOrder(CreateOrderRequest request) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Order getOrder(Long id) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Page<Order> getMyOrders(Long customerId, Pageable pageable) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Order cancelOrder(Long id) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Order markPaid(Long id) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Order markConfirmed(Long id) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
