package com.ecommerce.product.domain.repository;

import com.ecommerce.common.config.JpaAuditingConfig;
import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.product.domain.model.Brand;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.ProductImage;
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
class ProductImageRepositoryTest {

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainersConfig.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainersConfig.MYSQL::getUsername);
        registry.add("spring.datasource.password", TestContainersConfig.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    private Product product;

    @BeforeEach
    void setUp() {
        productImageRepository.deleteAll();
        productRepository.deleteAll();
        brandRepository.deleteAll();

        Brand brand = brandRepository.save(Brand.create("Iron Heart", null, "JP", "WORKWEAR", 1975, null, null));
        product = productRepository.save(Product.create(brand, "iron-heart-denim", "DENIM", null,
                new BigDecimal("350.0000"), "USD", null, null, null, null, "DENIM", "SELVEDGE"));
    }

    @Test
    void save_shouldPersistImage() {
        ProductImage image = ProductImage.create(product, "https://example.com/image1.jpg", (short) 0, true);

        ProductImage saved = productImageRepository.save(image);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUrl()).isEqualTo("https://example.com/image1.jpg");
        assertThat(saved.getSortOrder()).isEqualTo((short) 0);
        assertThat(saved.getIsPrimary()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void save_shouldDefaultSortOrderAndIsPrimary() {
        ProductImage image = ProductImage.create(product, "https://example.com/image2.jpg", null, null);

        ProductImage saved = productImageRepository.save(image);

        assertThat(saved.getSortOrder()).isEqualTo((short) 0);
        assertThat(saved.getIsPrimary()).isFalse();
    }

    @Test
    void clearPrimaryByProductId_shouldUnsetPrimaryImages() {
        productImageRepository.save(ProductImage.create(product, "https://example.com/img1.jpg", (short) 0, true));
        productImageRepository.save(ProductImage.create(product, "https://example.com/img2.jpg", (short) 1, false));

        productImageRepository.clearPrimaryByProductId(product.getId());
        productImageRepository.flush();

        productImageRepository.findAll().forEach(img -> assertThat(img.getIsPrimary()).isFalse());
    }
}
