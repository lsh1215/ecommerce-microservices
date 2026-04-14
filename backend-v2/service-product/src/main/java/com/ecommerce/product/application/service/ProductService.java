package com.ecommerce.product.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.ProductErrorCode;
import com.ecommerce.product.application.dto.CreateProductCommand;
import com.ecommerce.product.application.dto.ProductSearchCommand;
import com.ecommerce.product.application.dto.UpdateProductCommand;
import com.ecommerce.product.domain.model.Brand;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.ProductVariant;
import com.ecommerce.product.domain.repository.BrandRepository;
import com.ecommerce.product.domain.repository.ProductQueryRepository;
import com.ecommerce.product.domain.repository.ProductRepository;
import com.ecommerce.product.domain.repository.ProductVariantRepository;
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

    /** 서비스 간 스냅샷 제공을 위해 부모 상품 정보를 포함한 Variant를 조회한다. */
    public ProductVariant getVariantDetail(Long variantId) {
        return productVariantRepository.findWithProductAndBrandById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
    }

    /**
     * 비관적 락을 사용하여 Variant의 재고를 예약한다.
     * 주문 생성 시 초과 판매 방지를 위해 호출된다.
     */
    @Transactional
    public ProductVariant reserveStock(Long variantId, int quantity) {
        ProductVariant variant = productVariantRepository.findWithLockById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
        variant.reserveStock(quantity);
        return variant;
    }

    /**
     * 이전에 예약된 재고를 해제한다 (주문 실패 시 보상 처리).
     */
    @Transactional
    public ProductVariant releaseStock(Long variantId, int quantity) {
        ProductVariant variant = productVariantRepository.findWithLockById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
        variant.releaseStock(quantity);
        return variant;
    }
}
