package com.ecommerce.product.domain.repository;

import com.ecommerce.common.config.JpaAuditingConfig;
import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.product.domain.model.Brand;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.ProductVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({TestContainersConfig.class, JpaAuditingConfig.class})
class ProductVariantRepositoryTest {

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainersConfig.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainersConfig.MYSQL::getUsername);
        registry.add("spring.datasource.password", TestContainersConfig.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    private Product product;

    @BeforeEach
    void setUp() {
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
        brandRepository.deleteAll();

        Brand brand = brandRepository.save(Brand.create("Iron Heart", null, "JP", "WORKWEAR", 1975, null, null));
        product = productRepository.save(Product.create(brand, "iron-heart-634s", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null,
                new BigDecimal("21.0"), "DENIM", "SELVEDGE"));
    }

    @Test
    void save_shouldPersistVariantWithGeneratedPublicId() {
        ProductVariant variant = ProductVariant.create(product, "IH-634S-21OZ-32", "32",
                "Indigo", "#1a237e", null, null,
                new BigDecimal("54.0"), new BigDecimal("46.0"), new BigDecimal("64.0"),
                new BigDecimal("72.0"), new BigDecimal("40.0"), new BigDecimal("86.0"),
                new BigDecimal("31.0"), new BigDecimal("20.0"));

        ProductVariant saved = productVariantRepository.save(variant);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPublicId()).isNotNull().hasSize(26);
        assertThat(saved.getSku()).isEqualTo("IH-634S-21OZ-32");
        assertThat(saved.getSizeLabel()).isEqualTo("32");
        assertThat(saved.getMeasChestCm()).isEqualByComparingTo(new BigDecimal("54.0"));
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findByPublicId_shouldReturnVariantWhenExists() {
        ProductVariant variant = ProductVariant.create(product, "IH-634S-21OZ-34", "34",
                null, null, null, null, null, null, null, null, null, null, null, null);
        ProductVariant saved = productVariantRepository.save(variant);

        Optional<ProductVariant> found = productVariantRepository.findByPublicId(saved.getPublicId());

        assertThat(found).isPresent();
        assertThat(found.get().getSku()).isEqualTo("IH-634S-21OZ-34");
    }

    @Test
    void existsBySku_shouldReturnTrueWhenExists() {
        productVariantRepository.save(ProductVariant.create(product, "IH-634S-21OZ-36", "36",
                null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(productVariantRepository.existsBySku("IH-634S-21OZ-36")).isTrue();
    }

    @Test
    void existsBySku_shouldReturnFalseWhenNotExists() {
        assertThat(productVariantRepository.existsBySku("NONEXISTENT-SKU")).isFalse();
    }
}
