package com.ecommerce.drop.api.controller;

import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.drop.domain.model.DropEvent;
import com.ecommerce.drop.domain.model.DropProduct;
import com.ecommerce.drop.domain.repository.DropEventRepository;
import com.ecommerce.drop.domain.repository.DropProductRepository;
import com.ecommerce.drop.domain.repository.DropStatusHistoryRepository;
import com.ecommerce.inventory.domain.model.Inventory;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class DropEventControllerTest {

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
    private DropEventRepository dropEventRepository;

    @Autowired
    private DropProductRepository dropProductRepository;

    @Autowired
    private DropStatusHistoryRepository dropStatusHistoryRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @BeforeEach
    void setUp() {
        dropStatusHistoryRepository.deleteAll();
        dropProductRepository.deleteAll();
        dropEventRepository.deleteAll();
    }

    private Map<String, Object> createDropRequest(String title) {
        return Map.of(
                "title", title,
                "description", "Test drop event",
                "startsAt", "2026-04-01T10:00:00",
                "endsAt", "2026-04-01T22:00:00"
        );
    }

    @Test
    void createDropEvent_shouldReturn201WithCreatedEvent() throws Exception {
        mockMvc.perform(post("/api/drops")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDropRequest("Spring Drop"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.publicId", notNullValue()))
                .andExpect(jsonPath("$.data.title", is("Spring Drop")))
                .andExpect(jsonPath("$.data.status", is("ANNOUNCED")))
                .andExpect(jsonPath("$.data.createdAt", notNullValue()));
    }

    @Test
    void createDropEvent_shouldReturn400WhenTitleIsBlank() throws Exception {
        Map<String, Object> request = Map.of(
                "title", "",
                "description", "desc",
                "startsAt", "2026-04-01T10:00:00",
                "endsAt", "2026-04-01T22:00:00"
        );

        mockMvc.perform(post("/api/drops")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getDropEvent_shouldReturn200WhenFound() throws Exception {
        DropEvent event = DropEvent.create("Get Test", "Desc",
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 22, 0));
        DropEvent saved = dropEventRepository.save(event);

        mockMvc.perform(get("/api/drops/{publicId}", saved.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.title", is("Get Test")))
                .andExpect(jsonPath("$.data.status", is("ANNOUNCED")));
    }

    @Test
    void getDropEvent_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/drops/{publicId}", "nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void listDropEvents_shouldReturnPaginatedList() throws Exception {
        dropEventRepository.save(DropEvent.create("Drop 1", "D1",
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 22, 0)));
        dropEventRepository.save(DropEvent.create("Drop 2", "D2",
                LocalDateTime.of(2026, 4, 2, 10, 0),
                LocalDateTime.of(2026, 4, 2, 22, 0)));

        mockMvc.perform(get("/api/drops").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements", is(2)));
    }

    @Test
    void transitionStatus_shouldReturn200WithUpdatedStatus() throws Exception {
        DropEvent event = DropEvent.create("Transition Test", "Desc",
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 22, 0));
        DropEvent saved = dropEventRepository.save(event);

        mockMvc.perform(patch("/api/drops/{publicId}/status", saved.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "OPEN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("OPEN")));
    }

    @Test
    void transitionStatus_shouldReturn400OnInvalidTransition() throws Exception {
        DropEvent event = DropEvent.create("Invalid Transition", "Desc",
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 22, 0));
        DropEvent saved = dropEventRepository.save(event);

        mockMvc.perform(patch("/api/drops/{publicId}/status", saved.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "SELLING"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void addDropProduct_shouldReturn201WithCreatedProduct() throws Exception {
        DropEvent event = DropEvent.create("Product Drop", "Desc",
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 22, 0));
        DropEvent saved = dropEventRepository.save(event);

        Inventory inventory = Inventory.create(500L);
        inventory.adjust(100);
        inventoryRepository.save(inventory);

        Map<String, Object> request = Map.of(
                "productVariantId", 500L,
                "allocatedQuantity", 50,
                "dropPriceAmount", 199.99,
                "dropPriceCurrency", "USD"
        );

        mockMvc.perform(post("/api/drops/{publicId}/products", saved.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.publicId", notNullValue()))
                .andExpect(jsonPath("$.data.productVariantId", is(500)))
                .andExpect(jsonPath("$.data.allocatedQuantity", is(50)))
                .andExpect(jsonPath("$.data.soldQuantity", is(0)));
    }

    @Test
    void addDropProduct_shouldReturn400WhenAllocationExceeded() throws Exception {
        DropEvent event = DropEvent.create("Exceeded Drop", "Desc",
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 22, 0));
        DropEvent saved = dropEventRepository.save(event);

        Inventory inventory = Inventory.create(600L);
        inventory.adjust(10);
        inventoryRepository.save(inventory);

        Map<String, Object> request = Map.of(
                "productVariantId", 600L,
                "allocatedQuantity", 100,
                "dropPriceAmount", 99.99,
                "dropPriceCurrency", "USD"
        );

        mockMvc.perform(post("/api/drops/{publicId}/products", saved.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void removeDropProduct_shouldReturn200() throws Exception {
        DropEvent event = DropEvent.create("Remove Drop", "Desc",
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 22, 0));
        DropEvent savedEvent = dropEventRepository.save(event);

        DropProduct product = DropProduct.create(savedEvent, 700L, 10,
                new BigDecimal("50.00"), "USD");
        DropProduct savedProduct = dropProductRepository.save(product);

        mockMvc.perform(delete("/api/drop-products/{publicId}", savedProduct.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    void removeDropProduct_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(delete("/api/drop-products/{publicId}", "nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }
}
