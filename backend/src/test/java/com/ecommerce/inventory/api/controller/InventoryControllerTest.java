package com.ecommerce.inventory.api.controller;

import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.inventory.domain.model.Inventory;
import com.ecommerce.inventory.domain.model.InventoryEvent;
import com.ecommerce.inventory.domain.repository.InventoryEventRepository;
import com.ecommerce.inventory.domain.repository.InventoryRepository;
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

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class InventoryControllerTest {

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
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryEventRepository inventoryEventRepository;

    @BeforeEach
    void setUp() {
        inventoryEventRepository.deleteAll();
        inventoryRepository.deleteAll();
    }

    @Test
    void getByVariantId_shouldReturn200WithInventoryData() throws Exception {
        Inventory inventory = Inventory.create(10L);
        inventory.adjust(50);
        Inventory saved = inventoryRepository.save(inventory);

        mockMvc.perform(get("/api/inventory/variants/{variantId}", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(saved.getId().intValue())))
                .andExpect(jsonPath("$.data.productVariantId", is(10)))
                .andExpect(jsonPath("$.data.quantityAvailable", is(50)))
                .andExpect(jsonPath("$.data.quantityReserved", is(0)))
                .andExpect(jsonPath("$.data.quantitySold", is(0)))
                .andExpect(jsonPath("$.data.updatedAt", notNullValue()));
    }

    @Test
    void getByVariantId_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/inventory/variants/{variantId}", 9999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void adjust_shouldReturn200WithUpdatedInventory() throws Exception {
        Inventory inventory = Inventory.create(20L);
        inventoryRepository.save(inventory);

        Map<String, Object> request = Map.of(
                "quantityChange", 30,
                "reason", "Initial stock"
        );

        mockMvc.perform(put("/api/inventory/variants/{variantId}/adjust", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.quantityAvailable", is(30)));
    }

    @Test
    void adjust_shouldAllowNegativeDelta() throws Exception {
        Inventory inventory = Inventory.create(21L);
        inventory.adjust(50);
        inventoryRepository.save(inventory);

        Map<String, Object> request = Map.of(
                "quantityChange", -10,
                "reason", "Stock correction"
        );

        mockMvc.perform(put("/api/inventory/variants/{variantId}/adjust", 21L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantityAvailable", is(40)));
    }

    @Test
    void adjust_shouldReturn404WhenVariantNotFound() throws Exception {
        Map<String, Object> request = Map.of("quantityChange", 10);

        mockMvc.perform(put("/api/inventory/variants/{variantId}/adjust", 9999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void getEvents_shouldReturn200WithEventList() throws Exception {
        Inventory inventory = Inventory.create(30L);
        Inventory saved = inventoryRepository.save(inventory);

        inventoryEventRepository.save(InventoryEvent.create(
                saved.getId(), "ADJUSTMENT", "ADMIN", 100, null, null, "Initial"));
        inventoryEventRepository.save(InventoryEvent.create(
                saved.getId(), "RESERVED", "SYSTEM", -5, 1L, null, null));

        mockMvc.perform(get("/api/inventory/{inventoryId}/events", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void getEvents_shouldReturn404WhenInventoryNotFound() throws Exception {
        mockMvc.perform(get("/api/inventory/{inventoryId}/events", 9999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void adjustAndGetEvents_shouldRecordAuditEvent() throws Exception {
        Inventory inventory = Inventory.create(40L);
        Inventory saved = inventoryRepository.save(inventory);

        Map<String, Object> request = Map.of(
                "quantityChange", 25,
                "reason", "Restock"
        );

        mockMvc.perform(put("/api/inventory/variants/{variantId}/adjust", 40L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/inventory/{inventoryId}/events", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].eventType", is("ADJUSTMENT")))
                .andExpect(jsonPath("$.data[0].triggerType", is("ADMIN")))
                .andExpect(jsonPath("$.data[0].quantityChange", is(25)))
                .andExpect(jsonPath("$.data[0].reason", is("Restock")));
    }
}
