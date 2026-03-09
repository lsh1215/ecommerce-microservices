package com.ecommerce.product.infra.persistence;

import com.ecommerce.common.config.JpaAuditingConfig;
import com.ecommerce.common.config.QueryDslConfig;
import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.product.api.dto.request.ProductSearchRequest;
import com.ecommerce.product.domain.model.Brand;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.repository.BrandRepository;
import com.ecommerce.product.domain.repository.ProductRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({TestContainersConfig.class, JpaAuditingConfig.class, QueryDslConfig.class, ProductQueryRepository.class})
class ProductQueryRepositoryTest {

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainersConfig.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainersConfig.MYSQL::getUsername);
        registry.add("spring.datasource.password", TestContainersConfig.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private ProductQueryRepository productQueryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    private Brand ironHeart;
    private Brand redWing;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        brandRepository.deleteAll();

        ironHeart = brandRepository.save(Brand.create("Iron Heart", null, "JP", "WORKWEAR", 1975, null, null));
        redWing = brandRepository.save(Brand.create("Red Wing", null, "US", "AMERICANA", 1905, null, null));

        productRepository.save(Product.create(ironHeart, "iron-heart-21oz-selvedge", "DENIM", "1950s_WORKWEAR",
                new BigDecimal("350.0000"), "USD", null, null, null,
                new BigDecimal("21.0"), "DENIM", "SELVEDGE"));

        productRepository.save(Product.create(ironHeart, "iron-heart-flannel", "SHIRT", null,
                new BigDecimal("250.0000"), "USD", null, null, null, null, "COTTON", null));

        productRepository.save(Product.create(redWing, "red-wing-iron-ranger", "ACCESSORY", "1950s_WORKWEAR",
                new BigDecimal("320.0000"), "USD", null, null, null, null, null, null));
    }

    @Test
    void search_shouldFilterByBrandId() {
        ProductSearchRequest request = new ProductSearchRequest(
                ironHeart.getId(), null, null, null, null, null, null, 0, 20, "createdAt", "desc");

        Page<Product> result = productQueryRepository.search(request, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allSatisfy(p ->
                assertThat(p.getBrand().getId()).isEqualTo(ironHeart.getId()));
    }

    @Test
    void search_shouldFilterByCategory() {
        ProductSearchRequest request = new ProductSearchRequest(
                null, "DENIM", null, null, null, null, null, 0, 20, "createdAt", "desc");

        Page<Product> result = productQueryRepository.search(request, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCategory()).isEqualTo("DENIM");
    }

    @Test
    void search_shouldFilterByPriceRange() {
        ProductSearchRequest request = new ProductSearchRequest(
                null, null, null, null, null,
                new BigDecimal("300"), new BigDecimal("360"), 0, 20, "createdAt", "desc");

        Page<Product> result = productQueryRepository.search(request, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void search_shouldFilterByCombination() {
        ProductSearchRequest request = new ProductSearchRequest(
                ironHeart.getId(), "DENIM", null, "DENIM", "SELVEDGE",
                null, null, 0, 20, "createdAt", "desc");

        Page<Product> result = productQueryRepository.search(request, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSlug()).isEqualTo("iron-heart-21oz-selvedge");
    }

    @Test
    void search_shouldReturnAllWhenNoFilters() {
        ProductSearchRequest request = new ProductSearchRequest(
                null, null, null, null, null, null, null, 0, 20, "createdAt", "desc");

        Page<Product> result = productQueryRepository.search(request, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    void search_shouldSortByBasePriceAsc() {
        ProductSearchRequest request = new ProductSearchRequest(
                null, null, null, null, null, null, null, 0, 20, "basePrice", "asc");

        Page<Product> result = productQueryRepository.search(request, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getBasePriceAmount())
                .isLessThanOrEqualTo(result.getContent().get(1).getBasePriceAmount());
    }

    @Test
    void search_shouldPaginateCorrectly() {
        ProductSearchRequest request = new ProductSearchRequest(
                null, null, null, null, null, null, null, 0, 2, "createdAt", "desc");

        Page<Product> result = productQueryRepository.search(request, PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    void search_shouldFilterByEra() {
        ProductSearchRequest request = new ProductSearchRequest(
                null, null, "1950s_WORKWEAR", null, null, null, null, 0, 20, "createdAt", "desc");

        Page<Product> result = productQueryRepository.search(request, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
    }
}
