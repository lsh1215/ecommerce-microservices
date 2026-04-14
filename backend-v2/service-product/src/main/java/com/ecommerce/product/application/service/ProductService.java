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
     * Create a new product under the specified brand.
     * Validates brand existence before creation.
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
     * Update product details and optionally transition product status.
     */
    @Transactional
    public Product updateProduct(Long id, UpdateProductCommand command) {
        Product product = getProduct(id);
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
        Product product = getProduct(id);
        productRepository.delete(product);
    }

    /** Fetch variant with its parent product for internal service-to-service snapshot. */
    public ProductVariant getVariantDetail(Long variantId) {
        return productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
    }

    /**
     * Reserve stock for a variant using pessimistic locking.
     * Called during order creation to prevent overselling.
     */
    @Transactional
    public ProductVariant reserveStock(Long variantId, int quantity) {
        ProductVariant variant = productVariantRepository.findWithLockById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
        variant.reserveStock(quantity);
        return variant;
    }

    /**
     * Release previously reserved stock (compensation on order failure).
     */
    @Transactional
    public ProductVariant releaseStock(Long variantId, int quantity) {
        ProductVariant variant = productVariantRepository.findWithLockById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
        variant.releaseStock(quantity);
        return variant;
    }
}
