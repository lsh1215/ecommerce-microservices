package com.ecommerce.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.order.application.dto.OrderDetailResult;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.model.ShippingAddress;
import com.ecommerce.order.domain.model.VariantSnapshot;
import com.ecommerce.order.domain.repository.OrderRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:0"})
class OrderServiceQueryTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void getOrderReturnsResultWithItemsOutsidePersistenceContext() {
        Order saved = orderRepository.saveAndFlush(orderWithItem("ORD-Q-" + System.nanoTime(), 1L));

        OrderDetailResult result = orderService.getOrder(saved.getId());

        assertThat(result.id()).isEqualTo(saved.getId());
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().productName()).isEqualTo("Query Product");
    }

    @Test
    void getMyOrdersReturnsPagedResultsWithItems() {
        Long customerId = System.nanoTime();
        orderRepository.saveAndFlush(orderWithItem("ORD-L1-" + customerId, customerId));
        orderRepository.saveAndFlush(orderWithItem("ORD-L2-" + customerId, customerId));

        Page<OrderDetailResult> result = orderService.getMyOrders(customerId, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .allSatisfy(order -> assertThat(order.items()).hasSize(1));
    }

    private Order orderWithItem(String orderNumber, Long customerId) {
        Order order = Order.create(customerId, orderNumber, address(), null);
        order.addItem(OrderItem.create(
                new VariantSnapshot(10L, 100L, "Query Product", "M", "Black", BigDecimal.valueOf(10000)),
                2));
        return order;
    }

    private ShippingAddress address() {
        return new ShippingAddress("Lee", "010-0000-0000", "12345", "Seoul", null);
    }
}
