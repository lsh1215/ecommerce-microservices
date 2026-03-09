package com.ecommerce.product.domain.repository;

import com.ecommerce.common.config.JpaAuditingConfig;
import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.product.domain.model.Brand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({TestContainersConfig.class, JpaAuditingConfig.class})
class BrandRepositoryTest {

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainersConfig.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainersConfig.MYSQL::getUsername);
        registry.add("spring.datasource.password", TestContainersConfig.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private BrandRepository brandRepository;

    @BeforeEach
    void setUp() {
        brandRepository.deleteAll();
    }

    @Test
    void save_shouldPersistBrandWithGeneratedPublicId() {
        Brand brand = Brand.create("Iron Heart", null, "JP", "WORKWEAR", 1975, "Premium denim brand", null);

        Brand saved = brandRepository.save(brand);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPublicId()).isNotNull();
        assertThat(saved.getPublicId()).hasSize(26);
        assertThat(saved.getName()).isEqualTo("Iron Heart");
        assertThat(saved.getSlug()).isEqualTo("iron-heart");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void save_shouldUseProvidedSlugWhenSpecified() {
        Brand brand = Brand.create("Iron Heart", "custom-slug", "JP", "WORKWEAR", 1975, null, null);

        Brand saved = brandRepository.save(brand);

        assertThat(saved.getSlug()).isEqualTo("custom-slug");
    }

    @Test
    void findBySlug_shouldReturnBrandWhenExists() {
        Brand brand = Brand.create("Red Wing", null, "US", "AMERICANA", 1905, null, null);
        brandRepository.save(brand);

        Optional<Brand> found = brandRepository.findBySlug("red-wing");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Red Wing");
    }

    @Test
    void findBySlug_shouldReturnEmptyWhenNotExists() {
        Optional<Brand> found = brandRepository.findBySlug("nonexistent-brand");

        assertThat(found).isEmpty();
    }

    @Test
    void existsBySlug_shouldReturnTrueWhenExists() {
        Brand brand = Brand.create("Carhartt", null, "US", "WORKWEAR", 1889, null, null);
        brandRepository.save(brand);

        assertThat(brandRepository.existsBySlug("carhartt")).isTrue();
    }

    @Test
    void existsBySlug_shouldReturnFalseWhenNotExists() {
        assertThat(brandRepository.existsBySlug("no-such-brand")).isFalse();
    }

    @Test
    void existsByName_shouldReturnTrueWhenExists() {
        Brand brand = Brand.create("Levi's", null, "US", "AMERICANA", 1853, null, null);
        brandRepository.save(brand);

        assertThat(brandRepository.existsByName("Levi's")).isTrue();
    }

    @Test
    void existsByName_shouldReturnFalseWhenNotExists() {
        assertThat(brandRepository.existsByName("Unknown Brand")).isFalse();
    }
}
