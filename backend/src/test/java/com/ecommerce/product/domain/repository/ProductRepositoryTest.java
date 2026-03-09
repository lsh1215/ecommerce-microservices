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
class ProductRepositoryTest {

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainersConfig.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainersConfig.MYSQL::getUsername);
        registry.add("spring.datasource.password", TestContainersConfig.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    private Brand brand;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        brandRepository.deleteAll();
        brand = brandRepository.save(Brand.create("Iron Heart", null, "JP", "WORKWEAR", 1975, null, null));
    }

    @Test
    void save_shouldPersistProductWithGeneratedPublicId() {
        Product product = Product.create(brand, "iron-heart-21oz-selvedge", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null,
                new BigDecimal("21.0"), "DENIM", "SELVEDGE");

        Product saved = productRepository.save(product);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPublicId()).isNotNull().hasSize(26);
        assertThat(saved.getSlug()).isEqualTo("iron-heart-21oz-selvedge");
        assertThat(saved.getCategory()).isEqualTo("DENIM");
        assertThat(saved.getBasePriceAmount()).isEqualByComparingTo(new BigDecimal("350.0000"));
        assertThat(saved.getBrand().getId()).isEqualTo(brand.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findByPublicId_shouldReturnProductWhenExists() {
        Product product = Product.create(brand, "iron-heart-flannel", "SHIRT", null,
                new BigDecimal("250.0000"), "USD", null, null, null, null, "COTTON", null);
        Product saved = productRepository.save(product);

        Optional<Product> found = productRepository.findByPublicId(saved.getPublicId());

        assertThat(found).isPresent();
        assertThat(found.get().getSlug()).isEqualTo("iron-heart-flannel");
    }

    @Test
    void findByPublicId_shouldReturnEmptyWhenNotExists() {
        Optional<Product> found = productRepository.findByPublicId("01ARZ3NDEKTSV4RRFFQ69G5FAV");

        assertThat(found).isEmpty();
    }

    @Test
    void existsBySlug_shouldReturnTrueWhenExists() {
        productRepository.save(Product.create(brand, "iron-heart-denim", "DENIM", null,
                new BigDecimal("300.0000"), "USD", null, null, null, null, null, null));

        assertThat(productRepository.existsBySlug("iron-heart-denim")).isTrue();
    }

    @Test
    void existsBySlug_shouldReturnFalseWhenNotExists() {
        assertThat(productRepository.existsBySlug("nonexistent-slug")).isFalse();
    }

    @Test
    void searchByKeyword_shouldFindBySlug() {
        productRepository.save(Product.create(brand, "iron-heart-denim-jacket", "OUTERWEAR", null,
                new BigDecimal("500.0000"), "USD", null, null, null, null, "DENIM", null));
        productRepository.save(Product.create(brand, "iron-heart-flannel-shirt", "SHIRT", null,
                new BigDecimal("250.0000"), "USD", null, null, null, null, "COTTON", null));

        Page<Product> result = productRepository.searchByKeyword("denim", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSlug()).isEqualTo("iron-heart-denim-jacket");
    }

    @Test
    void searchByKeyword_shouldFindByTranslationName() {
        Product product = Product.create(brand, "iron-heart-21oz", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null, null, "DENIM", null);
        ProductTranslation translation = ProductTranslation.create(product, "en", "21oz Selvedge Denim", null);
        product.getTranslations().add(translation);
        productRepository.save(product);

        Page<Product> result = productRepository.searchByKeyword("Selvedge", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void generateSlug_shouldCombineBrandSlugAndProductName() {
        String slug = Product.generateSlug("rrl", "Slim Fit Selvedge");
        assertThat(slug).isEqualTo("rrl-slim-fit-selvedge");
    }
}
