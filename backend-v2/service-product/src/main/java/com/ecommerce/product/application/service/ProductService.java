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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
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

    @Transactional
    public ProductVariant reserveStock(Long variantId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK,
                    "Reserve quantity must be positive");
        }
        ProductVariant variant = productVariantRepository.findByIdForUpdate(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
        variant.reserveStock(quantity);
        return variant;
    }

    @Transactional
    public ProductVariant reserveStock(Long orderId, Long variantId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK,
                    "Reserve quantity must be positive");
        }
        var existingReservation = stockReservationRepository.findByOrderIdAndVariantId(orderId, variantId);
        if (existingReservation.isPresent()) {
            // 동일 주문/옵션 예약은 이미 재고를 차감했으므로 다시 차감하지 않는다.
            return productVariantRepository.findById(variantId)
                    .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
        }

        ProductVariant variant = productVariantRepository.findByIdForUpdate(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
        variant.reserveStock(quantity);
        stockReservationRepository.save(StockReservation.reserve(orderId, variantId, quantity));
        return variant;
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
        StockReservation reservation = stockReservationRepository.findByOrderIdAndVariantId(orderId, variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.RESERVATION_NOT_FOUND));
        int claimed = stockReservationRepository.markReleasedIfReserved(
                reservation.getId(),
                StockReservationStatus.RESERVED,
                StockReservationStatus.RELEASED);
        if (claimed == 1) {
            productVariantRepository.increaseStock(reservation.getVariantId(), reservation.getQuantity());
        }
        return productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
    }
}
