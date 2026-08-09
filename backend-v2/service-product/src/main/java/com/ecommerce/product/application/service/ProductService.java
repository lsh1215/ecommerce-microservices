package com.ecommerce.product.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.ProductErrorCode;
import com.ecommerce.product.application.dto.CreateProductCommand;
import com.ecommerce.product.application.dto.ProductSearchCommand;
import com.ecommerce.product.application.dto.UpdateProductCommand;
import com.ecommerce.product.domain.model.Brand;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.ProductVariant;
import com.ecommerce.product.domain.model.StockReservation;
import com.ecommerce.product.domain.model.StockReservationStatus;
import com.ecommerce.product.domain.repository.BrandRepository;
import com.ecommerce.product.domain.repository.ProductQueryRepository;
import com.ecommerce.product.domain.repository.ProductRepository;
import com.ecommerce.product.domain.repository.ProductVariantRepository;
import com.ecommerce.product.domain.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductQueryRepository productQueryRepository;
    private final BrandRepository brandRepository;
    private final StockReservationRepository stockReservationRepository;
    private final StockReservationStore stockReservationStore;
    private final StockReserveDbAdmit stockReserveDbAdmit;

    /**
     * 예약 경로의 동기/비동기 처리 모드. {@code sync}(기본값)는 기존 동작 그대로
     * Redis 예약 성공 직후 {@code stock_reservation} row를 같은 트랜잭션에서 INSERT한다.
     * {@code async}는 동기 INSERT를 생략하고, Redis Lua가 함께 적재한 settle queue를
     * {@link com.ecommerce.product.infra.redis.StockReservationSettler}가 비동기로 드레인하여
     * row를 채운다.
     */
    @Value("${reserve.settle.mode:sync}")
    private String reserveSettleMode = "sync";

    /**
     * 지정한 브랜드 하위에 신규 상품을 생성한다.
     * 생성 전 브랜드 존재 여부를 검증한다.
     */
    @Transactional
    public Product createProduct(CreateProductCommand command) {
        Brand brand = brandRepository.findById(command.brandId())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.BRAND_NOT_FOUND));
        Product product = Product.create(brand, command.name(), command.description(),
                command.price(), command.category());
        return productRepository.save(product);
    }

    /**
     * 상품 정보를 수정하고, 필요 시 상품 상태를 전이한다.
     */
    @Transactional
    public Product updateProduct(Long id, UpdateProductCommand command) {
        Product product = findProduct(id);
        product.update(command.name(), command.description(), command.price(), command.category());
        if (command.status() != null) {
            switch (command.status()) {
                case ACTIVE -> product.activate();
                case INACTIVE -> product.deactivate();
            }
        }
        return product;
    }

    public Product getProduct(Long id) {
        return productRepository.findWithVariantsAndImagesById(id)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    public Page<Product> searchProducts(ProductSearchCommand command, Pageable pageable) {
        return productQueryRepository.search(
                command.keyword(), command.brandId(), command.category(),
                command.minPrice(), command.maxPrice(), pageable);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = findProduct(id);
        productRepository.delete(product);
    }

    /** 서비스 간 상품 스냅샷 생성을 위해 상위 상품 정보를 함께 조회한다. */
    public ProductVariant getVariantDetail(Long variantId) {
        return productVariantRepository.findWithProductAndBrandById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
    }

    /**
     * 조건부 UPDATE 한 번으로 옵션 재고를 예약한다.
     */
    @Transactional
    public ProductVariant reserveStock(Long variantId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK,
                    "Reserve quantity must be positive");
        }
        int affected = productVariantRepository.decreaseStock(variantId, quantity);
        if (affected == 0) {
            ProductVariant variant = productVariantRepository.findById(variantId)
                    .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK,
                    String.format("Requested %d but only %d available",
                            quantity, variant.getStockQuantity()));
        }
        return productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
    }

    /**
     * 조건부 UPDATE 대신 Redis-only admit(async)와 DB-backed admit(sync/fallback)을
     * 분기한다. 이 메서드 자체는 트랜잭션을 절대 열지 않는다({@link Propagation#NOT_SUPPORTED}):
     * async 모드에서 Redis만으로 admit이 끝나는 경로(코드 1/2/0)가 DB 커넥션을 단 하나도
     * 점유하지 않도록 하기 위함이다. DB가 필요한 경로(sync 전체, 그리고 async의 -1 폴백)는
     * 별도 빈인 {@link StockReserveDbAdmit}로 위임한다 — 같은 빈 내에서 {@code @Transactional}
     * 메서드를 self-invocation하면 Spring AOP 프록시를 우회해 트랜잭션이 걸리지 않으므로,
     * 반드시 프록시를 거치는 별도 빈을 통해 호출해야 한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ProductVariant reserveStock(Long orderId, Long variantId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK,
                    "Reserve quantity must be positive");
        }
        if (!isAsyncSettleMode()) {
            return stockReserveDbAdmit.reserveSync(orderId, variantId, quantity);
        }

        // Async settle mode's throughput lever: admits the reservation with ZERO DB
        // round-trips via reserveRedisOnly, provided the available-stock snapshot was
        // preloaded into Redis (preloadAvailable). A successful admit (or a duplicate
        // replay of the same order) returns a detached, id-only ProductVariant — no DB
        // read is needed for the response. Oversell-0 still holds: this Redis capacity
        // check is advisory only, and confirmReservation retains the authoritative
        // decreaseStock WHERE stock >= quantity DB backstop.
        int code = stockReservationStore.reserveRedisOnly(variantId, orderId, quantity);
        if (code == 1 || code == 2) {
            return ProductVariant.reference(variantId);
        }
        if (code == 0) {
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK,
                    String.format("Requested %d exceeds reserved capacity for variant %d",
                            quantity, variantId));
        }
        // code == -1: the available-stock snapshot hasn't been preloaded for this variant
        // yet. Fall back to the DB-backed admit path exactly as before (no DB save in async
        // mode), then opportunistically preload Redis so subsequent requests for this
        // variant take the pure-Redis path above (self-warming).
        return stockReserveDbAdmit.reserveDbFallback(orderId, variantId, quantity);
    }

    /** 부하 테스트 등에서 사전에 Redis-only reserve 경로를 워밍업하기 위한 진입점. */
    public void preloadReservationStock(Long variantId) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
        stockReservationStore.preloadAvailable(variantId, variant.getStockQuantity());
    }

    private boolean isAsyncSettleMode() {
        return "async".equalsIgnoreCase(reserveSettleMode);
    }

    /**
     * {@code stock_reservation} row를 조회하되, async settle 모드에서 아직 settler가
     * 적재하지 않은 row는 Redis의 admit 결과로부터 재구성한다.
     *
     * <p>sync 모드에서는 이 폴백을 절대 타지 않는다 (기존 동작과 byte-identical 유지).
     * Redis에도 없으면 RESERVATION_NOT_FOUND. settler와의 경합으로 INSERT가
     * unique 제약을 위반하면 이미 적재된 row를 재조회해 멱등하게 처리한다.
     */
    private StockReservation loadOrReconstructReservation(Long orderId, Long variantId) {
        var found = stockReservationRepository.findByOrderIdAndVariantId(orderId, variantId);
        if (found.isPresent()) {
            return found.get();
        }
        if (isAsyncSettleMode()) {
            var qtyOpt = stockReservationStore.findReservedQuantity(variantId, orderId);
            if (qtyOpt.isPresent()) {
                try {
                    stockReservationRepository.save(StockReservation.reserve(orderId, variantId, qtyOpt.get()));
                } catch (DataIntegrityViolationException raced) {
                    // settler(또는 다른 요청)가 먼저 적재했다 — 멱등하게 처리한다.
                }
                return stockReservationRepository.findByOrderIdAndVariantId(orderId, variantId)
                        .orElseThrow(() -> new BusinessException(ProductErrorCode.RESERVATION_NOT_FOUND));
            }
        }
        throw new BusinessException(ProductErrorCode.RESERVATION_NOT_FOUND);
    }

    /**
     * 주문 보상을 위해 예약된 재고를 해제한다.
     */
    @Transactional
    public ProductVariant releaseStock(Long variantId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ProductErrorCode.INVALID_VARIANT_OPERATION,
                    "Release quantity must be positive");
        }
        int affected = productVariantRepository.increaseStock(variantId, quantity);
        if (affected == 0) {
            throw new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND);
        }
        return productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
    }

    /**
     * 예약 이력을 기준으로 재고를 해제한다.
     *
     * <p>상태를 RESERVED에서 RELEASED로 바꾼 호출만 재고를 증가시킨다.
     * 이미 RELEASED인 예약은 다시 들어와도 재고를 중복 복구하지 않는다.
     */
    @Transactional
    public ProductVariant releaseReservation(Long orderId, Long variantId) {
        StockReservation reservation = loadOrReconstructReservation(orderId, variantId);
        int claimed = stockReservationRepository.markReleasedIfReserved(
                reservation.getId(),
                StockReservationStatus.RESERVED,
                StockReservationStatus.RELEASED);
        if (claimed == 1) {
            stockReservationStore.release(reservation.getVariantId(), reservation.getOrderId());
        }
        return productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
    }

    @Transactional
    public ProductVariant confirmReservation(Long orderId, Long variantId) {
        StockReservation reservation = loadOrReconstructReservation(orderId, variantId);
        if (reservation.isConfirmed()) {
            return productVariantRepository.findById(variantId)
                    .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
        }
        if (reservation.isReleased()) {
            throw new BusinessException(ProductErrorCode.RESERVATION_NOT_FOUND);
        }
        int claimed = stockReservationRepository.markConfirmedIfReserved(
                reservation.getId(),
                StockReservationStatus.RESERVED,
                StockReservationStatus.CONFIRMED);
        if (claimed == 1) {
            int affected = productVariantRepository.decreaseStock(variantId, reservation.getQuantity());
            if (affected == 0) {
                ProductVariant variant = productVariantRepository.findById(variantId)
                        .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
                throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK,
                        String.format("Requested %d but only %d available",
                                reservation.getQuantity(), variant.getStockQuantity()));
            }
            stockReservationStore.release(reservation.getVariantId(), reservation.getOrderId());
        }
        return productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
    }
}
