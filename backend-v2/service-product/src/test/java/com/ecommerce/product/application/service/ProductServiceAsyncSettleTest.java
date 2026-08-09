package com.ecommerce.product.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.product.domain.model.ProductVariant;
import com.ecommerce.product.domain.model.StockReservation;
import com.ecommerce.product.domain.repository.BrandRepository;
import com.ecommerce.product.domain.repository.ProductQueryRepository;
import com.ecommerce.product.domain.repository.ProductRepository;
import com.ecommerce.product.domain.repository.ProductVariantRepository;
import com.ecommerce.product.domain.repository.StockReservationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Verifies the {@code reserve.settle.mode} gate on
 * {@link ProductService#reserveStock(Long, Long, int)}: {@code sync} (default) keeps
 * the synchronous {@code stock_reservation} INSERT; {@code async} skips it and relies
 * on {@code StockReservationSettler} draining the Redis settle queue instead.
 */
class ProductServiceAsyncSettleTest {

    private static final Long ORDER_ID = 1L;
    private static final Long VARIANT_ID = 10L;
    private static final int QUANTITY = 3;

    private StockReservationRepository stockReservationRepository;
    private StockReservationStore stockReservationStore;
    private ProductVariantRepository productVariantRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        ProductRepository productRepository = mock(ProductRepository.class);
        productVariantRepository = mock(ProductVariantRepository.class);
        ProductQueryRepository productQueryRepository = mock(ProductQueryRepository.class);
        BrandRepository brandRepository = mock(BrandRepository.class);
        stockReservationRepository = mock(StockReservationRepository.class);
        stockReservationStore = mock(StockReservationStore.class);

        productService = new ProductService(productRepository, productVariantRepository,
                productQueryRepository, brandRepository, stockReservationRepository, stockReservationStore,
                new StockReserveDbAdmit(productVariantRepository, stockReservationRepository, stockReservationStore));

        ProductVariant variant = mock(ProductVariant.class);
        when(variant.getStockQuantity()).thenReturn(10);
        when(productVariantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
        when(stockReservationRepository.findByOrderIdAndVariantId(ORDER_ID, VARIANT_ID))
                .thenReturn(Optional.empty());
        when(stockReservationStore.reserve(VARIANT_ID, ORDER_ID, QUANTITY, 10)).thenReturn(true);
        when(stockReservationStore.reserveRedisOnly(VARIANT_ID, ORDER_ID, QUANTITY)).thenReturn(-1);
    }

    @Test
    void syncModeIsTheDefaultAndKeepsTheSynchronousInsert() {
        productService.reserveStock(ORDER_ID, VARIANT_ID, QUANTITY);

        verify(stockReservationRepository, times(1)).save(any(StockReservation.class));
    }

    @Test
    void asyncModeSkipsTheSynchronousInsert() {
        ReflectionTestUtils.setField(productService, "reserveSettleMode", "async");

        ProductVariant result = productService.reserveStock(ORDER_ID, VARIANT_ID, QUANTITY);

        assertThat(result).isNotNull();
        verify(stockReservationRepository, never()).save(any(StockReservation.class));
        verify(stockReservationStore, times(1)).reserve(eq(VARIANT_ID), eq(ORDER_ID), eq(QUANTITY), anyInt());
    }
}
