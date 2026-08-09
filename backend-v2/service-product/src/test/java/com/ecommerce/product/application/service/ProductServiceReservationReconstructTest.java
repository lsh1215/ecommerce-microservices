package com.ecommerce.product.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.ProductErrorCode;
import com.ecommerce.product.domain.model.ProductVariant;
import com.ecommerce.product.domain.model.StockReservation;
import com.ecommerce.product.domain.model.StockReservationStatus;
import com.ecommerce.product.domain.repository.BrandRepository;
import com.ecommerce.product.domain.repository.ProductQueryRepository;
import com.ecommerce.product.domain.repository.ProductRepository;
import com.ecommerce.product.domain.repository.ProductVariantRepository;
import com.ecommerce.product.domain.repository.StockReservationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Verifies that {@link ProductService#confirmReservation(Long, Long)} and
 * {@link ProductService#releaseReservation(Long, Long)} tolerate a not-yet-settled
 * {@code stock_reservation} row in async settle mode by reconstructing it from the
 * Redis {@link StockReservationStore}. A valid (admitted) reservation must never be
 * rejected as {@code RESERVATION_NOT_FOUND} just because the async settler lagged.
 * Sync mode (the default) must never take this fallback path.
 */
class ProductServiceReservationReconstructTest {

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
                mock(StockReserveDbAdmit.class));
    }

    private void enableAsyncMode() {
        ReflectionTestUtils.setField(productService, "reserveSettleMode", "async");
    }

    private ProductVariant variantWithStock(int stockQuantity) {
        ProductVariant variant = mock(ProductVariant.class);
        when(variant.getStockQuantity()).thenReturn(stockQuantity);
        return variant;
    }

    @Test
    void confirmReconstructsFromRedisWhenRowNotYetSettled() {
        enableAsyncMode();
        StockReservation reconstructed = StockReservation.reserve(ORDER_ID, VARIANT_ID, QUANTITY);

        when(stockReservationRepository.findByOrderIdAndVariantId(ORDER_ID, VARIANT_ID))
                .thenReturn(Optional.empty(), Optional.of(reconstructed));
        when(stockReservationStore.findReservedQuantity(VARIANT_ID, ORDER_ID))
                .thenReturn(Optional.of(QUANTITY));
        when(stockReservationRepository.markConfirmedIfReserved(
                any(), eq(StockReservationStatus.RESERVED), eq(StockReservationStatus.CONFIRMED)))
                .thenReturn(1);
        when(productVariantRepository.decreaseStock(VARIANT_ID, QUANTITY)).thenReturn(1);
        ProductVariant variant = variantWithStock(7);
        when(productVariantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));

        ProductVariant result = productService.confirmReservation(ORDER_ID, VARIANT_ID);

        assertThat(result.getStockQuantity()).isEqualTo(7);
        ArgumentCaptor<StockReservation> captor = ArgumentCaptor.forClass(StockReservation.class);
        verify(stockReservationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(captor.getValue().getVariantId()).isEqualTo(VARIANT_ID);
        assertThat(captor.getValue().getQuantity()).isEqualTo(QUANTITY);
        verify(productVariantRepository, times(1)).decreaseStock(VARIANT_ID, QUANTITY);
        verify(stockReservationStore, times(1)).release(VARIANT_ID, ORDER_ID);
    }

    @Test
    void releaseReconstructsFromRedisWhenRowNotYetSettled() {
        enableAsyncMode();
        StockReservation reconstructed = StockReservation.reserve(ORDER_ID, VARIANT_ID, QUANTITY);

        when(stockReservationRepository.findByOrderIdAndVariantId(ORDER_ID, VARIANT_ID))
                .thenReturn(Optional.empty(), Optional.of(reconstructed));
        when(stockReservationStore.findReservedQuantity(VARIANT_ID, ORDER_ID))
                .thenReturn(Optional.of(QUANTITY));
        when(stockReservationRepository.markReleasedIfReserved(
                any(), eq(StockReservationStatus.RESERVED), eq(StockReservationStatus.RELEASED)))
                .thenReturn(1);
        ProductVariant variant = variantWithStock(10);
        when(productVariantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));

        ProductVariant result = productService.releaseReservation(ORDER_ID, VARIANT_ID);

        assertThat(result).isNotNull();
        verify(stockReservationRepository, times(1)).save(any(StockReservation.class));
        verify(stockReservationStore, times(1)).release(VARIANT_ID, ORDER_ID);
    }

    @Test
    void confirmThrowsWhenAbsentInBothDbAndStore() {
        enableAsyncMode();
        when(stockReservationRepository.findByOrderIdAndVariantId(ORDER_ID, VARIANT_ID))
                .thenReturn(Optional.empty());
        when(stockReservationStore.findReservedQuantity(VARIANT_ID, ORDER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.confirmReservation(ORDER_ID, VARIANT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.RESERVATION_NOT_FOUND);

        verify(stockReservationRepository, never()).save(any(StockReservation.class));
    }

    @Test
    void syncModeDoesNotReconstruct() {
        when(stockReservationRepository.findByOrderIdAndVariantId(ORDER_ID, VARIANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.confirmReservation(ORDER_ID, VARIANT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.RESERVATION_NOT_FOUND);

        verify(stockReservationStore, never()).findReservedQuantity(any(), any());
        verify(stockReservationRepository, never()).save(any(StockReservation.class));
    }

    @Test
    void reconstructionIsIdempotentUnderRacingInsert() {
        enableAsyncMode();
        StockReservation settledByRacer = StockReservation.reserve(ORDER_ID, VARIANT_ID, QUANTITY);

        when(stockReservationRepository.findByOrderIdAndVariantId(ORDER_ID, VARIANT_ID))
                .thenReturn(Optional.empty(), Optional.of(settledByRacer));
        when(stockReservationStore.findReservedQuantity(VARIANT_ID, ORDER_ID))
                .thenReturn(Optional.of(QUANTITY));
        when(stockReservationRepository.save(any(StockReservation.class)))
                .thenThrow(new DataIntegrityViolationException("uk_stock_reservation_order_variant"));
        when(stockReservationRepository.markConfirmedIfReserved(
                any(), eq(StockReservationStatus.RESERVED), eq(StockReservationStatus.CONFIRMED)))
                .thenReturn(1);
        when(productVariantRepository.decreaseStock(VARIANT_ID, QUANTITY)).thenReturn(1);
        ProductVariant variant = variantWithStock(7);
        when(productVariantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));

        ProductVariant result = productService.confirmReservation(ORDER_ID, VARIANT_ID);

        assertThat(result.getStockQuantity()).isEqualTo(7);
        verify(stockReservationRepository, times(2)).findByOrderIdAndVariantId(ORDER_ID, VARIANT_ID);
        verify(productVariantRepository, times(1)).decreaseStock(VARIANT_ID, QUANTITY);
    }

    @Test
    void oversellStaysZero() {
        enableAsyncMode();
        StockReservation reconstructed = StockReservation.reserve(ORDER_ID, VARIANT_ID, QUANTITY);

        when(stockReservationRepository.findByOrderIdAndVariantId(ORDER_ID, VARIANT_ID))
                .thenReturn(Optional.empty(), Optional.of(reconstructed));
        when(stockReservationStore.findReservedQuantity(VARIANT_ID, ORDER_ID))
                .thenReturn(Optional.of(QUANTITY));
        when(stockReservationRepository.markConfirmedIfReserved(
                any(), eq(StockReservationStatus.RESERVED), eq(StockReservationStatus.CONFIRMED)))
                .thenReturn(1);
        when(productVariantRepository.decreaseStock(VARIANT_ID, QUANTITY)).thenReturn(0);
        ProductVariant variant = variantWithStock(1);
        when(productVariantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));

        assertThatThrownBy(() -> productService.confirmReservation(ORDER_ID, VARIANT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.INSUFFICIENT_STOCK);

        verify(stockReservationStore, never()).release(VARIANT_ID, ORDER_ID);
    }
}
