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
class ProductServiceRedisReservationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void reserveStockForOrderKeepsDbStockUntilPaymentConfirmation() {
        ProductVariant variant = saveVariant(10);

        ProductVariant reserved = productService.reserveStock(1L, variant.getId(), 3);

        assertThat(reserved.getStockQuantity()).isEqualTo(10);
        assertThat(productService.getVariantDetail(variant.getId()).getStockQuantity()).isEqualTo(10);

        ProductVariant confirmed = productService.confirmReservation(1L, variant.getId());

        assertThat(confirmed.getStockQuantity()).isEqualTo(7);
    }

    @Test
    void releaseReservationBeforePaymentConfirmationOnlyReleasesRedisReservation() {
        ProductVariant variant = saveVariant(10);
        productService.reserveStock(1L, variant.getId(), 3);

        ProductVariant released = productService.releaseReservation(1L, variant.getId());

        assertThat(released.getStockQuantity()).isEqualTo(10);
        assertThat(productService.releaseReservation(1L, variant.getId()).getStockQuantity()).isEqualTo(10);
    }

    @Test
    void reserveStockForOrderRejectsWhenRedisReservedQuantityExceedsDbStock() {
        ProductVariant variant = saveVariant(5);
        productService.reserveStock(1L, variant.getId(), 3);

        assertThatThrownBy(() -> productService.reserveStock(2L, variant.getId(), 3))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.INSUFFICIENT_STOCK);

        assertThat(productService.getVariantDetail(variant.getId()).getStockQuantity()).isEqualTo(5);
    }

    @Test
    void confirmReservationIsIdempotentForSameOrderAndVariant() {
        ProductVariant variant = saveVariant(10);
        productService.reserveStock(1L, variant.getId(), 3);

        ProductVariant first = productService.confirmReservation(1L, variant.getId());
        ProductVariant second = productService.confirmReservation(1L, variant.getId());

        assertThat(first.getStockQuantity()).isEqualTo(7);
        assertThat(second.getStockQuantity()).isEqualTo(7);
    }

    private ProductVariant saveVariant(int stockQuantity) {
        String suffix = stockQuantity + "-" + System.nanoTime();
        Brand brand = brandRepository.save(Brand.create(
                "Redis Reservation Brand " + suffix,
                "brand for redis reservation test",
                null,
                "KR"));
        Product product = Product.create(
                brand,
                "Redis Reservation Product " + suffix,
                "product for redis reservation test",
                BigDecimal.valueOf(10_000),
                "test");
        ProductVariant variant = product.addVariant(
                "REDIS-RESERVATION-" + suffix,
                "M",
                "Black",
                stockQuantity,
                null);
        productRepository.saveAndFlush(product);
        return variant;
    }
}
