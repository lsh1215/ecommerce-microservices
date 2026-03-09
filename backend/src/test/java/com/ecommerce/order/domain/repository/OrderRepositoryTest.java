package com.ecommerce.order.domain.repository;

import com.ecommerce.common.config.JpaAuditingConfig;
import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.model.OrderStatusHistory;
import com.ecommerce.order.domain.model.Orders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({TestContainersConfig.class, JpaAuditingConfig.class})
class OrderRepositoryTest {

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainersConfig.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainersConfig.MYSQL::getUsername);
        registry.add("spring.datasource.password", TestContainersConfig.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    private Orders createTestOrder(Long customerId, String idempotencyKey) {
        Orders order = Orders.create(customerId, idempotencyKey,
                new BigDecimal("100.00"), "USD", "123 Main St");

        OrderItem item = OrderItem.create(1L, 2, "Test Product", "Test Brand",
                new BigDecimal("50.00"), "USD", "M", "SKU-001");
        order.addItem(item);

        OrderStatusHistory history = OrderStatusHistory.create(null, "PENDING", null);
        order.addStatusHistory(history);

        return order;
    }

    @Test
    void save_shouldPersistOrderWithItemsAndHistory() {
        Orders order = createTestOrder(1L, "key-1");

        Orders saved = orderRepository.save(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPublicId()).isNotNull().hasSize(26);
        assertThat(saved.getCustomerId()).isEqualTo(1L);
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getStatusHistories()).hasSize(1);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByPublicId_shouldReturnOrderWhenExists() {
        Orders saved = orderRepository.save(createTestOrder(1L, "key-2"));

        Optional<Orders> found = orderRepository.findByPublicId(saved.getPublicId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findByPublicId_shouldReturnEmptyWhenNotExists() {
        Optional<Orders> found = orderRepository.findByPublicId("nonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    void findByCustomerId_shouldReturnPaginatedOrders() {
        orderRepository.save(createTestOrder(10L, "key-a"));
        orderRepository.save(createTestOrder(10L, "key-b"));
        orderRepository.save(createTestOrder(20L, "key-c"));

        Page<Orders> page = orderRepository.findByCustomerId(10L, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void existsByIdempotencyKey_shouldReturnTrueWhenExists() {
        orderRepository.save(createTestOrder(1L, "unique-key"));

        assertThat(orderRepository.existsByIdempotencyKey("unique-key")).isTrue();
    }

    @Test
    void existsByIdempotencyKey_shouldReturnFalseWhenNotExists() {
        assertThat(orderRepository.existsByIdempotencyKey("nonexistent")).isFalse();
    }

    @Test
    void save_shouldCascadeOrderItems() {
        Orders order = createTestOrder(1L, "key-cascade");
        Orders saved = orderRepository.save(order);

        assertThat(saved.getItems().get(0).getId()).isNotNull();
        assertThat(saved.getItems().get(0).getProductName()).isEqualTo("Test Product");
    }

    @Test
    void save_shouldCascadeStatusHistory() {
        Orders order = createTestOrder(1L, "key-history");
        Orders saved = orderRepository.save(order);

        assertThat(saved.getStatusHistories().get(0).getId()).isNotNull();
        assertThat(saved.getStatusHistories().get(0).getNewStatus()).isEqualTo("PENDING");
        assertThat(saved.getStatusHistories().get(0).getCreatedAt()).isNotNull();
    }
}
