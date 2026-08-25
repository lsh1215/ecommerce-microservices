package com.ecommerce.product.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.product.domain.model.Brand;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.ProductVariant;
import com.ecommerce.product.domain.model.StockContention;
import com.ecommerce.product.domain.model.StockUnit;
import com.ecommerce.product.domain.model.StockUnitHolder;
import com.ecommerce.product.domain.model.StockUnitStatus;
import com.ecommerce.product.domain.repository.BrandRepository;
import com.ecommerce.product.domain.repository.ProductRepository;
import com.ecommerce.product.domain.repository.ProductVariantRepository;
import com.ecommerce.product.domain.repository.StockUnitRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 유닛의 주인이 식별자 하나가 아니라 {@code (종류, 식별자)} 짝으로 정해지는지 확인한다.
 *
 * <p>일반 예약의 주문 id와 선착순 확보의 Kafka offset은 서로 다른 번호 공간에서 나오므로
 * 같은 상품에서 같은 숫자가 나올 수 있다. 종류를 빼고 식별자만 비교하면 한쪽의 확정이
 * 다른 쪽의 유닛까지 끌고 간다.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:0"})
class FlashReserveHolderTest {

    /** 주문 id와 offset이 같은 값일 때를 노린다. */
    private static final long COLLIDING_ID = 7L;

    @Autowired private FlashReserveService flashReserveService;
    @Autowired private StockReservationService stockReservationService;
    @Autowired private ProductRepository productRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductVariantRepository productVariantRepository;
    @Autowired private StockUnitRepository stockUnitRepository;

    @Test
    void confirmingAnOrderDoesNotTouchAFlashUnitWithTheSameNumber() {
        ProductVariant variant = hotVariantWithUnits(4);

        stockReservationService.reserve(variant.getId(), COLLIDING_ID, 1);
        assertThat(flashReserveService.reserve(COLLIDING_ID, variant.getId(), 1)).isTrue();

        stockReservationService.confirm(variant.getId(), COLLIDING_ID, 1);

        assertThat(held(variant, StockUnitHolder.ORDER, StockUnitStatus.CONFIRMED)).isEqualTo(1);
        assertThat(held(variant, StockUnitHolder.FLASH, StockUnitStatus.RESERVED)).isEqualTo(1);
    }

    @Test
    void releasingAFlashUnitDoesNotReturnAnOrderUnitWithTheSameNumber() {
        ProductVariant variant = hotVariantWithUnits(4);

        stockReservationService.reserve(variant.getId(), COLLIDING_ID, 1);
        flashReserveService.reserve(COLLIDING_ID, variant.getId(), 1);

        assertThat(flashReserveService.release(COLLIDING_ID, variant.getId())).isTrue();

        assertThat(held(variant, StockUnitHolder.ORDER, StockUnitStatus.RESERVED)).isEqualTo(1);
        assertThat(held(variant, StockUnitHolder.FLASH, StockUnitStatus.RESERVED)).isZero();
        assertThat(stockUnitRepository.countByVariantIdAndStatus(
                variant.getId(), StockUnitStatus.AVAILABLE)).isEqualTo(3);
    }

    /** 재전송된 접수는 유닛을 다시 집지 않는다. granter가 결과를 다시 발행하는 근거다. */
    @Test
    void redeliveredOffsetReservesOnce() {
        ProductVariant variant = hotVariantWithUnits(4);

        assertThat(flashReserveService.reserve(11L, variant.getId(), 2)).isTrue();
        assertThat(flashReserveService.reserve(11L, variant.getId(), 2)).isTrue();

        assertThat(held(variant, StockUnitHolder.FLASH, 11L, StockUnitStatus.RESERVED)).isEqualTo(2);
        assertThat(stockUnitRepository.countByVariantIdAndStatus(
                variant.getId(), StockUnitStatus.AVAILABLE)).isEqualTo(2);
    }

    private long held(ProductVariant variant, StockUnitHolder holderType, StockUnitStatus status) {
        return held(variant, holderType, COLLIDING_ID, status);
    }

    private long held(ProductVariant variant, StockUnitHolder holderType, long holderId,
            StockUnitStatus status) {
        return stockUnitRepository.countByHolderTypeAndHolderIdAndVariantIdAndStatusIn(
                holderType, holderId, variant.getId(), List.of(status));
    }

    private ProductVariant hotVariantWithUnits(int units) {
        String tag = "FLASH-" + System.nanoTime();
        Brand brand = brandRepository.save(Brand.create("B-" + tag, null, null, null));
        Product product = productRepository.save(
                Product.create(brand, "P-" + tag, null, BigDecimal.valueOf(1000), "C"));
        ProductVariant added = product.addVariant("SKU-" + tag, "M", "BLACK", 0, null);
        ReflectionTestUtils.setField(added, "stockContention", StockContention.HOT);
        productRepository.save(product);
        ProductVariant variant = productVariantRepository.findAll().stream()
                .filter(v -> ("SKU-" + tag).equals(v.getSku()))
                .findFirst()
                .orElseThrow();

        List<StockUnit> seeded = new ArrayList<>();
        for (int i = 0; i < units; i++) {
            seeded.add(StockUnit.available(variant.getId()));
        }
        stockUnitRepository.saveAll(seeded);
        return variant;
    }
}
