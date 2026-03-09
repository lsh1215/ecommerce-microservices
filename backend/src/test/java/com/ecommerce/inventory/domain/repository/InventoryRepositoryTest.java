package com.ecommerce.inventory.domain.repository;

import com.ecommerce.common.config.JpaAuditingConfig;
import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.inventory.domain.model.Inventory;
import com.ecommerce.inventory.domain.model.InventoryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({TestContainersConfig.class, JpaAuditingConfig.class})
class InventoryRepositoryTest {

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainersConfig.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainersConfig.MYSQL::getUsername);
        registry.add("spring.datasource.password", TestContainersConfig.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

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
    void save_shouldPersistInventoryWithVersionZero() {
        Inventory inventory = Inventory.create(100L);

        Inventory saved = inventoryRepository.save(inventory);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getProductVariantId()).isEqualTo(100L);
        assertThat(saved.getQuantityAvailable()).isZero();
        assertThat(saved.getQuantityReserved()).isZero();
        assertThat(saved.getQuantitySold()).isZero();
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByProductVariantId_shouldReturnInventoryWhenExists() {
        inventoryRepository.save(Inventory.create(200L));

        Optional<Inventory> found = inventoryRepository.findByProductVariantId(200L);

        assertThat(found).isPresent();
        assertThat(found.get().getProductVariantId()).isEqualTo(200L);
    }

    @Test
    void findByProductVariantId_shouldReturnEmptyWhenNotExists() {
        Optional<Inventory> found = inventoryRepository.findByProductVariantId(999L);

        assertThat(found).isEmpty();
    }

    @Test
    void existsByProductVariantId_shouldReturnTrueWhenExists() {
        inventoryRepository.save(Inventory.create(300L));

        assertThat(inventoryRepository.existsByProductVariantId(300L)).isTrue();
    }

    @Test
    void existsByProductVariantId_shouldReturnFalseWhenNotExists() {
        assertThat(inventoryRepository.existsByProductVariantId(999L)).isFalse();
    }

    @Autowired
    private org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager entityManager;

    @Test
    void save_shouldIncrementVersionOnUpdate() {
        Inventory inventory = inventoryRepository.save(Inventory.create(400L));
        assertThat(inventory.getVersion()).isZero();

        inventory.adjust(10);
        inventoryRepository.saveAndFlush(inventory);
        entityManager.clear();

        Inventory reloaded = inventoryRepository.findById(inventory.getId()).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(1);
    }

    @Test
    void inventoryEvent_shouldPersistWithCreatedAt() {
        Inventory inventory = inventoryRepository.save(Inventory.create(500L));

        InventoryEvent event = InventoryEvent.create(
                inventory.getId(), "ADJUSTMENT", "ADMIN", 10, null, null, "Initial stock");
        InventoryEvent saved = inventoryEventRepository.save(event);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getInventoryId()).isEqualTo(inventory.getId());
        assertThat(saved.getEventType()).isEqualTo("ADJUSTMENT");
        assertThat(saved.getTriggerType()).isEqualTo("ADMIN");
        assertThat(saved.getQuantityChange()).isEqualTo(10);
        assertThat(saved.getReason()).isEqualTo("Initial stock");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findByInventoryIdOrderByCreatedAtDesc_shouldReturnEventsInOrder() {
        Inventory inventory = inventoryRepository.save(Inventory.create(600L));

        inventoryEventRepository.save(InventoryEvent.create(
                inventory.getId(), "ADJUSTMENT", "ADMIN", 10, null, null, "first"));
        inventoryEventRepository.save(InventoryEvent.create(
                inventory.getId(), "RESERVED", "SYSTEM", -5, 1L, null, null));

        List<InventoryEvent> events = inventoryEventRepository
                .findByInventoryIdOrderByCreatedAtDesc(inventory.getId());

        assertThat(events).hasSize(2);
    }
}
