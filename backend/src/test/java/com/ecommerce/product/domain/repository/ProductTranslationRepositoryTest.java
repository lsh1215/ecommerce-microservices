package com.ecommerce.product.domain.repository;

import com.ecommerce.common.config.JpaAuditingConfig;
import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.product.domain.model.Brand;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.ProductTranslation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({TestContainersConfig.class, JpaAuditingConfig.class})
class ProductTranslationRepositoryTest {

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainersConfig.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainersConfig.MYSQL::getUsername);
        registry.add("spring.datasource.password", TestContainersConfig.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private ProductTranslationRepository productTranslationRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    private Product product;

    @BeforeEach
    void setUp() {
        productTranslationRepository.deleteAll();
        productRepository.deleteAll();
        brandRepository.deleteAll();

        Brand brand = brandRepository.save(Brand.create("Iron Heart", null, "JP", "WORKWEAR", 1975, null, null));
        product = productRepository.save(Product.create(brand, "iron-heart-flannel", "SHIRT", null,
                new BigDecimal("250.0000"), "USD", null, null, null, null, "COTTON", null));
    }

    @Test
    void save_shouldPersistTranslation() {
        ProductTranslation translation = ProductTranslation.create(product, "en", "Ultra Heavy Flannel", "Warm flannel shirt");

        ProductTranslation saved = productTranslationRepository.save(translation);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getLocale()).isEqualTo("en");
        assertThat(saved.getName()).isEqualTo("Ultra Heavy Flannel");
        assertThat(saved.getDescription()).isEqualTo("Warm flannel shirt");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void existsByProductIdAndLocale_shouldReturnTrueWhenExists() {
        productTranslationRepository.save(ProductTranslation.create(product, "ko", "울트라 헤비 플란넬", null));

        assertThat(productTranslationRepository.existsByProductIdAndLocale(product.getId(), "ko")).isTrue();
    }

    @Test
    void existsByProductIdAndLocale_shouldReturnFalseWhenNotExists() {
        assertThat(productTranslationRepository.existsByProductIdAndLocale(product.getId(), "ja")).isFalse();
    }
}
