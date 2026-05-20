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

    /** Creates a product under an existing brand. */
    @Transactional
    public Product createProduct(CreateProductCommand command) {
        Brand brand = brandRepository.findById(command.brandId())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.BRAND_NOT_FOUND));
        Product product = Product.create(brand, command.name(), command.description(),
                command.price(), command.category());
        return productRepository.save(product);
    }

    /** Updates product details and applies a requested status transition. */
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

    /** Loads a variant with parent product data for inter-service snapshots. */
    public ProductVariant getVariantDetail(Long variantId) {
        return productVariantRepository.findWithProductAndBrandById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
    }

    /**
     * Reserves variant stock with one guarded UPDATE statement.
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
     * Releases previously reserved stock for order compensation.
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
}
