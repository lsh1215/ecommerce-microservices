package com.ecommerce.product.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.ProductErrorCode;
import com.ecommerce.product.domain.model.ProductVariant;
import com.ecommerce.product.domain.repository.BrandRepository;
import com.ecommerce.product.domain.repository.ProductQueryRepository;
import com.ecommerce.product.domain.repository.ProductRepository;
import com.ecommerce.product.domain.repository.ProductVariantRepository;
import com.ecommerce.product.domain.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Verifies that async settle mode's {@link ProductService#reserveStock(Long, Long, int)}
 * uses the pure-Redis {@link StockReservationStore#reserveRedisOnly} path with ZERO DB
 * reads/writes whenever the available-stock snapshot has been preloaded, delegates to
 * {@link StockReserveDbAdmit} (the only bean allowed to open a DB transaction) for the
 * DB-backed fallback (code -1) and for sync mode, and never takes the Redis-only path at
 * all in sync mode. {@code reserveStock} itself must never touch
 * {@code productVariantRepository}/{@code stockReservationRepository} directly.
 */
class ProductServiceRedisOnlyReserveTest {

    private static final Long ORDER_ID = 1L;
    private static final Long VARIANT_ID = 10L;
    private static final int QUANTITY = 3;

    private StockReservationRepository stockReservationRepository;
    private StockReservationStore stockReservationStore;
    private ProductVariantRepository productVariantRepository;
    private StockReserveDbAdmit stockReserveDbAdmit;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        ProductRepository productRepository = mock(ProductRepository.class);
        productVariantRepository = mock(ProductVariantRepository.class);
        ProductQueryRepository productQueryRepository = mock(ProductQueryRepository.class);
        BrandRepository brandRepository = mock(BrandRepository.class);
        stockReservationRepository = mock(StockReservationRepository.class);
        stockReservationStore = mock(StockReservationStore.class);
        stockReserveDbAdmit = mock(StockReserveDbAdmit.class);

        productService = new ProductService(productRepository, productVariantRepository,
                productQueryRepository, brandRepository, stockReservationRepository, stockReservationStore,
                stockReserveDbAdmit);
    }

    private void enableAsyncMode() {
        ReflectionTestUtils.setField(productService, "reserveSettleMode", "async");
    }

    @Test
    void redisOnlyAdmitSkipsAllDbReadsAndReturnsIdOnlyVariant() {
        enableAsyncMode();
        when(stockReservationStore.reserveRedisOnly(VARIANT_ID, ORDER_ID, QUANTITY)).thenReturn(1);

        ProductVariant result = productService.reserveStock(ORDER_ID, VARIANT_ID, QUANTITY);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(VARIANT_ID);
        verifyNoInteractions(productVariantRepository);
        verifyNoInteractions(stockReservationRepository);
        verifyNoInteractions(stockReserveDbAdmit);
    }

    @Test
    void redisOnlyDuplicateAdmitSkipsAllDbReadsAndReturnsIdOnlyVariant() {
        enableAsyncMode();
        when(stockReservationStore.reserveRedisOnly(VARIANT_ID, ORDER_ID, QUANTITY)).thenReturn(2);

        ProductVariant result = productService.reserveStock(ORDER_ID, VARIANT_ID, QUANTITY);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(VARIANT_ID);
        verifyNoInteractions(productVariantRepository);
        verifyNoInteractions(stockReservationRepository);
        verifyNoInteractions(stockReserveDbAdmit);
    }

    @Test
    void redisOnlyFullCapacityThrowsInsufficientStockWithoutAnyDbRead() {
        enableAsyncMode();
        when(stockReservationStore.reserveRedisOnly(VARIANT_ID, ORDER_ID, QUANTITY)).thenReturn(0);

        assertThatThrownBy(() -> productService.reserveStock(ORDER_ID, VARIANT_ID, QUANTITY))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.INSUFFICIENT_STOCK);

        verifyNoInteractions(productVariantRepository);
        verifyNoInteractions(stockReservationRepository);
        verifyNoInteractions(stockReserveDbAdmit);
    }

    @Test
    void redisOnlyNotPreloadedFallsBackToDbAdmitFallback() {
        enableAsyncMode();
        when(stockReservationStore.reserveRedisOnly(VARIANT_ID, ORDER_ID, QUANTITY)).thenReturn(-1);
        ProductVariant variant = mock(ProductVariant.class);
        when(stockReserveDbAdmit.reserveDbFallback(ORDER_ID, VARIANT_ID, QUANTITY)).thenReturn(variant);

        ProductVariant result = productService.reserveStock(ORDER_ID, VARIANT_ID, QUANTITY);

        assertThat(result).isSameAs(variant);
        verify(stockReserveDbAdmit, times(1)).reserveDbFallback(ORDER_ID, VARIANT_ID, QUANTITY);
        verify(stockReserveDbAdmit, never()).reserveSync(any(), any(), anyInt());
        // reserveStock itself never touches the repositories directly on this path;
        // StockReserveDbAdmit owns the DB reads/writes behind its own transaction.
        verifyNoInteractions(productVariantRepository);
        verifyNoInteractions(stockReservationRepository);
    }

    @Test
    void syncModeDelegatesToDbAdmitReserveSyncAndNeverCallsReserveRedisOnly() {
        ProductVariant variant = mock(ProductVariant.class);
        when(stockReserveDbAdmit.reserveSync(ORDER_ID, VARIANT_ID, QUANTITY)).thenReturn(variant);

        ProductVariant result = productService.reserveStock(ORDER_ID, VARIANT_ID, QUANTITY);

        assertThat(result).isSameAs(variant);
        verify(stockReserveDbAdmit, times(1)).reserveSync(ORDER_ID, VARIANT_ID, QUANTITY);
        verify(stockReserveDbAdmit, never()).reserveDbFallback(any(), any(), anyInt());
        verify(stockReservationStore, never()).reserveRedisOnly(anyLong(), anyLong(), anyInt());
        verifyNoInteractions(productVariantRepository);
        verifyNoInteractions(stockReservationRepository);
    }
}
