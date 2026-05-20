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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    void concurrentReserveStockDoesNotOversell() throws Exception {
        ProductVariant variant = saveVariant(5);
        int requestCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < requestCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    productService.reserveStock(variant.getId(), 1);
                    return true;
                } catch (BusinessException e) {
                    assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.INSUFFICIENT_STOCK);
                    return false;
                }
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        int successCount = 0;
        int failureCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(5, TimeUnit.SECONDS)) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        ProductVariant reloaded = productService.getVariantDetail(variant.getId());
        assertThat(successCount).isEqualTo(5);
        assertThat(failureCount).isEqualTo(15);
        assertThat(reloaded.getStockQuantity()).isZero();
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
