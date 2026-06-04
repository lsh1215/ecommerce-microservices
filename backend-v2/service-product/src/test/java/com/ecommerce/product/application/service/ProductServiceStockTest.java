package com.ecommerce.product.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.ProductErrorCode;
import com.ecommerce.product.domain.model.Brand;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.ProductVariant;
import com.ecommerce.product.domain.repository.BrandRepository;
import com.ecommerce.product.domain.repository.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:0"})
class ProductServiceStockTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void reserveStockUsesGuardedUpdate() {
        ProductVariant variant = saveVariant(10);

        ProductVariant reserved = productService.reserveStock(variant.getId(), 3);

        assertThat(reserved.getStockQuantity()).isEqualTo(7);
    }

    @Test
    void reserveStockRejectsInsufficientStockWithoutChangingQuantity() {
        ProductVariant variant = saveVariant(2);

        assertThatThrownBy(() -> productService.reserveStock(variant.getId(), 3))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.INSUFFICIENT_STOCK);

        ProductVariant reloaded = productService.getVariantDetail(variant.getId());
        assertThat(reloaded.getStockQuantity()).isEqualTo(2);
    }

    @Test
    void reserveStockRejectsNonPositiveQuantity() {
        ProductVariant variant = saveVariant(10);

        assertThatThrownBy(() -> productService.reserveStock(variant.getId(), 0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.INSUFFICIENT_STOCK);

        ProductVariant reloaded = productService.getVariantDetail(variant.getId());
        assertThat(reloaded.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void reserveStockForOrderIsIdempotentForSameOrderAndVariant() {
        ProductVariant variant = saveVariant(10);

        ProductVariant first = productService.reserveStock(1L, variant.getId(), 3);
        ProductVariant second = productService.reserveStock(1L, variant.getId(), 3);

        assertThat(first.getStockQuantity()).isEqualTo(10);
        assertThat(second.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void releaseReservationIsIdempotentForSameOrderAndVariant() {
        ProductVariant variant = saveVariant(10);
        productService.reserveStock(1L, variant.getId(), 3);

        ProductVariant first = productService.releaseReservation(1L, variant.getId());
        ProductVariant second = productService.releaseReservation(1L, variant.getId());

        assertThat(first.getStockQuantity()).isEqualTo(10);
        assertThat(second.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void releaseReservationRejectsMissingReservation() {
        ProductVariant variant = saveVariant(10);

        assertThatThrownBy(() -> productService.releaseReservation(1L, variant.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.RESERVATION_NOT_FOUND);

        ProductVariant reloaded = productService.getVariantDetail(variant.getId());
        assertThat(reloaded.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void releaseStockRejectsNonPositiveQuantity() {
        ProductVariant variant = saveVariant(10);

        assertThatThrownBy(() -> productService.releaseStock(variant.getId(), -1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.INVALID_VARIANT_OPERATION);

        ProductVariant reloaded = productService.getVariantDetail(variant.getId());
        assertThat(reloaded.getStockQuantity()).isEqualTo(10);
    }

    private ProductVariant saveVariant(int stockQuantity) {
        String suffix = stockQuantity + "-" + System.nanoTime();
        Brand brand = brandRepository.save(Brand.create(
                "Hot Row Brand " + suffix,
                "brand for stock test",
                null,
                "KR"));
        Product product = Product.create(
                brand,
                "Hot Row Product " + suffix,
                "product for stock test",
                BigDecimal.valueOf(10_000),
                "test");
        ProductVariant variant = product.addVariant(
                "HOT-ROW-" + suffix,
                "M",
                "Black",
                stockQuantity,
                null);
        productRepository.saveAndFlush(product);
        return variant;
    }
}
