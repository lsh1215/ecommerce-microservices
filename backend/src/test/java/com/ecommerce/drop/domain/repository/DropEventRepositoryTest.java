package com.ecommerce.drop.domain.repository;

import com.ecommerce.common.config.JpaAuditingConfig;
import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.drop.domain.model.DropEvent;
import com.ecommerce.drop.domain.model.DropProduct;
import com.ecommerce.drop.domain.model.DropStatusHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({TestContainersConfig.class, JpaAuditingConfig.class})
class DropEventRepositoryTest {

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainersConfig.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainersConfig.MYSQL::getUsername);
        registry.add("spring.datasource.password", TestContainersConfig.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private DropEventRepository dropEventRepository;

    @Autowired
    private DropProductRepository dropProductRepository;

    @Autowired
    private DropStatusHistoryRepository dropStatusHistoryRepository;

    @BeforeEach
    void setUp() {
        dropStatusHistoryRepository.deleteAll();
        dropProductRepository.deleteAll();
        dropEventRepository.deleteAll();
    }

    private DropEvent createAndSaveEvent(String title) {
        DropEvent event = DropEvent.create(title, "Description",
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 22, 0));
        return dropEventRepository.save(event);
    }

    @Test
    void save_shouldPersistDropEventWithGeneratedFields() {
        DropEvent saved = createAndSaveEvent("Spring Drop 2026");

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPublicId()).isNotNull().hasSize(26);
        assertThat(saved.getTitle()).isEqualTo("Spring Drop 2026");
        assertThat(saved.getStatus()).isEqualTo("ANNOUNCED");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByPublicId_shouldReturnEventWhenExists() {
        DropEvent saved = createAndSaveEvent("Test Drop");

        Optional<DropEvent> found = dropEventRepository.findByPublicId(saved.getPublicId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Test Drop");
    }

    @Test
    void findByPublicId_shouldReturnEmptyWhenNotExists() {
        Optional<DropEvent> found = dropEventRepository.findByPublicId("nonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    void dropProduct_shouldPersistWithDropEvent() {
        DropEvent event = createAndSaveEvent("Drop with Products");
        DropProduct product = DropProduct.create(event, 100L, 50,
                new BigDecimal("199.99"), "USD");
        DropProduct saved = dropProductRepository.save(product);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPublicId()).isNotNull().hasSize(26);
        assertThat(saved.getDropEvent().getId()).isEqualTo(event.getId());
        assertThat(saved.getAllocatedQuantity()).isEqualTo(50);
    }

    @Test
    void sumAllocatedQuantityByVariantId_shouldReturnTotalAllocation() {
        DropEvent event1 = createAndSaveEvent("Drop 1");
        DropEvent event2 = createAndSaveEvent("Drop 2");

        dropProductRepository.save(DropProduct.create(event1, 100L, 30,
                new BigDecimal("100.00"), "USD"));
        dropProductRepository.save(DropProduct.create(event2, 100L, 20,
                new BigDecimal("100.00"), "USD"));

        int total = dropProductRepository.sumAllocatedQuantityByVariantId(100L);

        assertThat(total).isEqualTo(50);
    }

    @Test
    void sumAllocatedQuantityByVariantId_shouldReturnZeroWhenNoProducts() {
        int total = dropProductRepository.sumAllocatedQuantityByVariantId(999L);

        assertThat(total).isZero();
    }

    @Test
    void dropStatusHistory_shouldPersistWithCreatedAt() {
        DropEvent event = createAndSaveEvent("Status Test");
        DropStatusHistory history = DropStatusHistory.create(event, null, "ANNOUNCED");
        DropStatusHistory saved = dropStatusHistoryRepository.save(history);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPreviousStatus()).isNull();
        assertThat(saved.getNewStatus()).isEqualTo("ANNOUNCED");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findByDropEventIdOrderByCreatedAtDesc_shouldReturnHistoryInOrder() {
        DropEvent event = createAndSaveEvent("History Test");

        dropStatusHistoryRepository.save(DropStatusHistory.create(event, null, "ANNOUNCED"));
        dropStatusHistoryRepository.save(DropStatusHistory.create(event, "ANNOUNCED", "OPEN"));

        List<DropStatusHistory> histories =
                dropStatusHistoryRepository.findByDropEventIdOrderByCreatedAtDesc(event.getId());

        assertThat(histories).hasSize(2);
    }

    @Test
    void findByPublicId_dropProduct_shouldReturnProductWhenExists() {
        DropEvent event = createAndSaveEvent("Product Lookup Test");
        DropProduct product = DropProduct.create(event, 200L, 10,
                new BigDecimal("50.00"), "KRW");
        DropProduct saved = dropProductRepository.save(product);

        Optional<DropProduct> found = dropProductRepository.findByPublicId(saved.getPublicId());

        assertThat(found).isPresent();
        assertThat(found.get().getProductVariantId()).isEqualTo(200L);
    }

    @Test
    void findByDropEventId_shouldReturnAllProductsForEvent() {
        DropEvent event = createAndSaveEvent("Multi Product Drop");
        dropProductRepository.save(DropProduct.create(event, 1L, 10,
                new BigDecimal("100.00"), "USD"));
        dropProductRepository.save(DropProduct.create(event, 2L, 20,
                new BigDecimal("200.00"), "USD"));

        List<DropProduct> products = dropProductRepository.findByDropEventId(event.getId());

        assertThat(products).hasSize(2);
    }
}
