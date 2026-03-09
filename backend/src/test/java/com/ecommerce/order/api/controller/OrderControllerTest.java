package com.ecommerce.order.api.controller;

import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.inventory.domain.model.Inventory;
import com.ecommerce.inventory.domain.repository.InventoryEventRepository;
import com.ecommerce.inventory.domain.repository.InventoryRepository;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.model.OrderStatusHistory;
import com.ecommerce.order.domain.model.Orders;
import com.ecommerce.order.domain.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class OrderControllerTest {

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainersConfig.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainersConfig.MYSQL::getUsername);
        registry.add("spring.datasource.password", TestContainersConfig.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryEventRepository inventoryEventRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        inventoryEventRepository.deleteAll();
        inventoryRepository.deleteAll();
    }

    private Orders saveTestOrder(Long customerId, String idempotencyKey, Long variantId) {
        Orders order = Orders.create(customerId, idempotencyKey,
                new BigDecimal("100.00"), "USD", "123 Main St");
        OrderItem item = OrderItem.create(variantId, 2, "Test Product", "Test Brand",
                new BigDecimal("50.00"), "USD", "M", "SKU-001");
        order.addItem(item);
        OrderStatusHistory history = OrderStatusHistory.create(null, "PENDING", null);
        order.addStatusHistory(history);
        return orderRepository.save(order);
    }

    private void setupInventory(Long variantId, int available, int reserved) {
        Inventory inventory = Inventory.create(variantId);
        inventory.adjust(available);
        if (reserved > 0) {
            inventory.reserve(reserved);
        }
        inventoryRepository.save(inventory);
    }

    @Test
    void getByPublicId_shouldReturn200WithOrderData() throws Exception {
        Orders saved = saveTestOrder(1L, "key-get", 1L);

        mockMvc.perform(get("/api/orders/{publicId}", saved.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.publicId", is(saved.getPublicId())))
                .andExpect(jsonPath("$.data.customerId", is(1)))
                .andExpect(jsonPath("$.data.status", is("PENDING")))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].productName", is("Test Product")))
                .andExpect(jsonPath("$.data.items[0].brandName", is("Test Brand")))
                .andExpect(jsonPath("$.data.createdAt", notNullValue()));
    }

    @Test
    void getByPublicId_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/orders/{publicId}", "nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void listByCustomerId_shouldReturnPaginatedOrders() throws Exception {
        saveTestOrder(10L, "key-list-1", 101L);
        saveTestOrder(10L, "key-list-2", 102L);
        saveTestOrder(20L, "key-list-3", 103L);

        mockMvc.perform(get("/api/orders")
                        .param("customerId", "10")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements", is(2)));
    }

    @Test
    void cancelOrder_shouldReturn200WithCancelledStatus() throws Exception {
        setupInventory(500L, 10, 2);
        Orders saved = saveTestOrder(1L, "key-cancel", 500L);

        mockMvc.perform(post("/api/orders/{publicId}/cancel", saved.getPublicId())
                        .param("reason", "Changed my mind"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));
    }

    @Test
    void cancelOrder_shouldReturn404WhenOrderNotFound() throws Exception {
        mockMvc.perform(post("/api/orders/{publicId}/cancel", "nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void cancelOrder_shouldReturn400WhenAlreadyCancelled() throws Exception {
        Orders saved = saveTestOrder(1L, "key-double-cancel", 600L);
        saved.transitionTo("CANCELLED");
        saved.addStatusHistory(OrderStatusHistory.create("PENDING", "CANCELLED", null));
        orderRepository.save(saved);

        mockMvc.perform(post("/api/orders/{publicId}/cancel", saved.getPublicId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void createOrder_shouldReturn400WhenRequestBodyInvalid() throws Exception {
        Map<String, Object> invalid = Map.of("customerId", 1);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }
}
