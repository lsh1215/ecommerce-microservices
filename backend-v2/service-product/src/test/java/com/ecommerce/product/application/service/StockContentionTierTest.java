package com.ecommerce.product.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.domain.model.Brand;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.ProductVariant;
import com.ecommerce.product.domain.model.StockContention;
import com.ecommerce.product.domain.model.StockShard;
import com.ecommerce.product.domain.model.StockUnit;
import com.ecommerce.product.domain.model.StockUnitStatus;
import com.ecommerce.product.domain.repository.BrandRepository;
import com.ecommerce.product.domain.repository.ProductRepository;
import com.ecommerce.product.domain.repository.ProductVariantRepository;
import com.ecommerce.product.domain.repository.StockShardRepository;
import com.ecommerce.product.domain.repository.StockUnitRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 세 경합 등급이 같은 보장을 지키는지 확인한다 — <b>있는 재고보다 더 팔지 않는다</b>.
 *
 * <p>등급마다 그 보장을 얻는 곳이 다르다. NORMAL은 조건부 UPDATE의 WHERE 절, POPULAR는
 * 샤드별 같은 조건, HOT은 존재하는 row 수 자체다. 재고보다 많은 요청을 동시에 넣어
 * 정확히 재고만큼만 성공하는지 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:0"})
class StockContentionTierTest {

    @Autowired private StockReservationService stockReservationService;
    @Autowired private ProductRepository productRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductVariantRepository productVariantRepository;
    @Autowired private StockShardRepository stockShardRepository;
    @Autowired private StockUnitRepository stockUnitRepository;

    private static final int STOCK = 30;
    private static final int ATTEMPTS = 80;

    @Test
    void normalTierSellsExactlyTheStockItHas() throws Exception {
        ProductVariant variant = variantWith(StockContention.NORMAL, STOCK);

        int succeeded = reserveConcurrently(variant.getId(), ATTEMPTS);

        assertThat(succeeded).isEqualTo(STOCK);
        assertThat(productVariantRepository.findById(variant.getId()).orElseThrow()
                .getStockQuantity()).isZero();
    }

    @Test
    void popularTierSellsExactlyTheStockItHas() throws Exception {
        ProductVariant variant = variantWith(StockContention.POPULAR, 0);
        // 샤드 6개에 5개씩 = 30. 샤드가 비면 가장 많은 샤드에서 끌어오는 폴백이
        // 동작해야 총 재고를 전부 팔 수 있다.
        for (int shardNo = 0; shardNo < 6; shardNo++) {
            stockShardRepository.save(StockShard.of(variant.getId(), shardNo, 5));
        }

        int succeeded = reserveConcurrently(variant.getId(), ATTEMPTS);

        assertThat(succeeded).isEqualTo(STOCK);
        assertThat(stockShardRepository.totalQuantity(variant.getId())).isZero();
    }

    @Test
    void hotTierSellsExactlyTheStockItHas() throws Exception {
        ProductVariant variant = variantWith(StockContention.HOT, 0);
        List<StockUnit> units = new ArrayList<>();
        for (int i = 0; i < STOCK; i++) {
            units.add(StockUnit.available(variant.getId()));
        }
        stockUnitRepository.saveAll(units);

        int succeeded = reserveConcurrently(variant.getId(), ATTEMPTS);

        assertThat(succeeded).isEqualTo(STOCK);
        assertThat(stockUnitRepository.countByVariantIdAndStatus(variant.getId(), StockUnitStatus.AVAILABLE)).isZero();
    }

    @Test
    void repeatedReservationForSameOrderDoesNotDeductTwice() {
        ProductVariant variant = variantWith(StockContention.NORMAL, 10);

        stockReservationService.reserve(variant.getId(), 1L, 3);
        stockReservationService.reserve(variant.getId(), 1L, 3);

        assertThat(productVariantRepository.findById(variant.getId()).orElseThrow()
                .getStockQuantity()).isEqualTo(7);
    }

    @Test
    void sameOrderWithDifferentQuantityIsRejected() {
        ProductVariant variant = variantWith(StockContention.NORMAL, 10);
        stockReservationService.reserve(variant.getId(), 2L, 3);

        assertThatThrownBy(() -> stockReservationService.reserve(variant.getId(), 2L, 5))
                .isInstanceOf(BusinessException.class);
    }

    /** 재고보다 많은 요청을 동시에 넣고 성공 건수를 센다. */
    private int reserveConcurrently(Long variantId, int attempts) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        for (int i = 0; i < attempts; i++) {
            long orderId = 1000L + i;
            pool.submit(() -> {
                try {
                    start.await();
                    stockReservationService.reserve(variantId, orderId, 1);
                    ok.incrementAndGet();
                } catch (Exception ignored) {
                    // 재고 부족은 정상 결과다.
                }
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        return ok.get();
    }

    private ProductVariant variantWith(StockContention contention, int stock) {
        // brand.name과 variant.sku에 유니크 제약이 있다. 테스트 메서드마다 새로 만들므로
        // 이름을 고정하면 두 번째 테스트에서 중복으로 깨진다.
        String tag = contention + "-" + System.nanoTime();
        Brand brand = brandRepository.save(Brand.create("B-" + tag, null, null, null));
        Product product = productRepository.save(
                Product.create(brand, "P-" + tag, null, BigDecimal.valueOf(1000), "C"));
        ProductVariant added = product.addVariant("SKU-" + tag, "M", "BLACK", stock, null);
        ReflectionTestUtils.setField(added, "stockContention", contention);
        productRepository.save(product);
        // cascade 저장 후 id가 채워진 인스턴스를 다시 읽는다. addVariant가 돌려준 참조는
        // 영속화 전 객체라 id가 없어, 그대로 쓰면 샤드·유닛의 variant_id가 null이 된다.
        return productVariantRepository.findAll().stream()
                .filter(v -> ("SKU-" + tag).equals(v.getSku()))
                .findFirst()
                .orElseThrow();
    }
}
