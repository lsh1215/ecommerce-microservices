package com.ecommerce.product.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.ProductErrorCode;
import com.ecommerce.product.application.dto.ProductListItemResult;
import com.ecommerce.product.application.dto.ProductSearchCommand;
import com.ecommerce.product.domain.model.Brand;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.ProductVariant;
import com.ecommerce.product.domain.repository.BrandRepository;
import com.ecommerce.product.domain.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

        assertThat(first.getStockQuantity()).isEqualTo(7);
        assertThat(second.getStockQuantity()).isEqualTo(7);
        assertThat(productService.getVariantDetail(variant.getId()).getStockQuantity()).isEqualTo(7);
    }

    @Test
    void reserveStockForOrderRejectsRepeatedReservationWithDifferentQuantity() {
        ProductVariant variant = saveVariant(10);
        productService.reserveStock(1L, variant.getId(), 3);

        assertThatThrownBy(() -> productService.reserveStock(1L, variant.getId(), 2))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.INVALID_VARIANT_OPERATION);

        assertThat(productService.getVariantDetail(variant.getId()).getStockQuantity()).isEqualTo(7);
    }

    @Test
    void reserveStockWithSnapshotReturnsVariantWithProductSnapshotData() {
        ProductVariant variant = saveVariant(10);

        ProductVariant reserved = productService.reserveStockWithSnapshot(1L, variant.getId(), 3);
        ProductVariant repeated = productService.reserveStockWithSnapshot(1L, variant.getId(), 3);

        assertThat(reserved.getId()).isEqualTo(variant.getId());
        assertThat(reserved.getProduct().getName()).startsWith("Hot Row Product");
        assertThat(reserved.effectivePrice()).isEqualByComparingTo(BigDecimal.valueOf(10_000));
        assertThat(reserved.getStockQuantity()).isEqualTo(7);
        assertThat(repeated.getId()).isEqualTo(variant.getId());
        assertThat(repeated.getStockQuantity()).isEqualTo(7);
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
    void releasedReservationCannotBeReservedAgainForSameOrderAndVariant() {
        ProductVariant variant = saveVariant(10);
        productService.reserveStock(1L, variant.getId(), 3);
        productService.releaseReservation(1L, variant.getId());

        assertThatThrownBy(() -> productService.reserveStock(1L, variant.getId(), 3))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.RESERVATION_NOT_FOUND);

        assertThat(productService.getVariantDetail(variant.getId()).getStockQuantity()).isEqualTo(10);
    }

    @Test
    void confirmReservationDoesNotDecrementStockAgain() {
        ProductVariant variant = saveVariant(10);
        productService.reserveStock(1L, variant.getId(), 3);

        ProductVariant first = productService.confirmReservation(1L, variant.getId());
        ProductVariant second = productService.confirmReservation(1L, variant.getId());

        assertThat(first.getStockQuantity()).isEqualTo(7);
        assertThat(second.getStockQuantity()).isEqualTo(7);
        assertThat(productService.getVariantDetail(variant.getId()).getStockQuantity()).isEqualTo(7);
    }

    @Test
    void confirmReservationsAndPublishConfirmsAllRequestedReservations() {
        ProductVariant first = saveVariant(10);
        ProductVariant second = saveVariant(10);
        productService.reserveStock(1L, first.getId(), 3);
        productService.reserveStock(1L, second.getId(), 2);

        productService.confirmReservationsAndPublish(1L, "ORD-001", List.of(first.getId(), second.getId()));
        productService.confirmReservationsAndPublish(1L, "ORD-001", List.of(first.getId(), second.getId()));

        assertThat(productService.getVariantDetail(first.getId()).getStockQuantity()).isEqualTo(7);
        assertThat(productService.getVariantDetail(second.getId()).getStockQuantity()).isEqualTo(8);
    }

    @Test
    void releaseReservationsAndPublishReleasesAllRequestedReservations() {
        ProductVariant first = saveVariant(10);
        ProductVariant second = saveVariant(10);
        productService.reserveStock(1L, first.getId(), 3);
        productService.reserveStock(1L, second.getId(), 2);

        productService.releaseReservationsAndPublish(1L, "ORD-001", List.of(first.getId(), second.getId()));
        productService.releaseReservationsAndPublish(1L, "ORD-001", List.of(first.getId(), second.getId()));

        assertThat(productService.getVariantDetail(first.getId()).getStockQuantity()).isEqualTo(10);
        assertThat(productService.getVariantDetail(second.getId()).getStockQuantity()).isEqualTo(10);
    }

    @Test
    void releaseReservationRejectsConfirmedReservation() {
        ProductVariant variant = saveVariant(10);
        productService.reserveStock(1L, variant.getId(), 3);
        productService.confirmReservation(1L, variant.getId());

        assertThatThrownBy(() -> productService.releaseReservation(1L, variant.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.INVALID_VARIANT_OPERATION);

        assertThat(productService.getVariantDetail(variant.getId()).getStockQuantity()).isEqualTo(7);
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

    @Test
    void searchProductsReturnsListProjectionWithPrimaryImage() {
        String suffix = "projection-" + System.nanoTime();
        Brand brand = brandRepository.save(Brand.create(
                "Projection Brand " + suffix,
                "brand for projection test",
                null,
                "KR"));
        Product product = Product.create(
                brand,
                "Projection Product " + suffix,
                "product for projection test",
                BigDecimal.valueOf(20_000),
                suffix);
        product.addImage("https://cdn.example.com/non-primary.jpg", 0, false);
        product.addImage("https://cdn.example.com/primary.jpg", 1, true);
        productRepository.saveAndFlush(product);

        Page<ProductListItemResult> result = productService.searchProducts(
                new ProductSearchCommand(null, brand.getId(), suffix, null, null),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        ProductListItemResult item = result.getContent().getFirst();
        assertThat(item.name()).startsWith("Projection Product");
        assertThat(item.brandName()).startsWith("Projection Brand");
        assertThat(item.primaryImageUrl()).isEqualTo("https://cdn.example.com/primary.jpg");
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
