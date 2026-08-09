package com.ecommerce.product.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.ProductErrorCode;
import com.ecommerce.product.domain.model.ProductVariant;
import com.ecommerce.product.domain.model.StockReservation;
import com.ecommerce.product.domain.repository.ProductVariantRepository;
import com.ecommerce.product.domain.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds the DB-backed reservation admit paths behind a real Spring proxy so that
 * {@code @Transactional} is honored no matter how the caller invokes it.
 * {@link ProductService#reserveStock(Long, Long, int)} deliberately carries no
 * transaction of its own so the pure-Redis async admit path (codes 1/2/0 from
 * {@link StockReservationStore#reserveRedisOnly}) acquires ZERO DB connections —
 * self-invocation of an {@code @Transactional} method from within the same bean would
 * bypass the Spring AOP proxy and silently run without a transaction. This component is
 * a separate bean so the proxy is always crossed for the two DB-backed paths below.
 */
@Component
@RequiredArgsConstructor
class StockReserveDbAdmit {

    private final ProductVariantRepository productVariantRepository;
    private final StockReservationRepository stockReservationRepository;
    private final StockReservationStore stockReservationStore;

    /** Sync settle mode's admit path: dedup, guard, reserve in Redis, then INSERT the row. */
    @Transactional
    public ProductVariant reserveSync(Long orderId, Long variantId, int quantity) {
        var existingReservation = stockReservationRepository.findByOrderIdAndVariantId(orderId, variantId);
        if (existingReservation.isPresent()) {
            // 동일 주문/옵션 예약은 이미 재고를 차감했으므로 다시 차감하지 않는다.
            return productVariantRepository.findById(variantId)
                    .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
        }

        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
        boolean reserved = stockReservationStore.reserve(variantId, orderId, quantity, variant.getStockQuantity());
        if (!reserved) {
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK,
                    String.format("Requested %d but only %d available",
                            quantity, variant.getStockQuantity()));
        }
        stockReservationRepository.save(StockReservation.reserve(orderId, variantId, quantity));
        return variant;
    }

    /**
     * Async settle mode's fallback admit path, used only when the Redis available-stock
     * snapshot hasn't been preloaded for this variant yet (code -1). No synchronous
     * {@code stock_reservation} INSERT here (the async settler drains that from the Redis
     * settle queue); this opportunistically preloads Redis afterward so subsequent
     * requests for this variant take the pure-Redis path (self-warming).
     */
    @Transactional
    public ProductVariant reserveDbFallback(Long orderId, Long variantId, int quantity) {
        var existingReservation = stockReservationRepository.findByOrderIdAndVariantId(orderId, variantId);
        if (existingReservation.isPresent()) {
            ProductVariant variant = productVariantRepository.findById(variantId)
                    .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
            stockReservationStore.preloadAvailable(variantId, variant.getStockQuantity());
            return variant;
        }

        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
        boolean reserved = stockReservationStore.reserve(variantId, orderId, quantity, variant.getStockQuantity());
        stockReservationStore.preloadAvailable(variantId, variant.getStockQuantity());
        if (!reserved) {
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK,
                    String.format("Requested %d but only %d available",
                            quantity, variant.getStockQuantity()));
        }
        return variant;
    }
}
