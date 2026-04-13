package com.ecommerce.product.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.ProductErrorCode;
import com.ecommerce.product.api.dto.request.CreateProductRequest;
import com.ecommerce.product.api.dto.request.UpdateProductRequest;
import com.ecommerce.product.api.dto.request.ProductSearchRequest;
import com.ecommerce.product.api.dto.response.VariantDetailResponse;
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

    @Transactional
    public Product createProduct(CreateProductRequest request) {
        Brand brand = brandRepository.findById(request.brandId())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.BRAND_NOT_FOUND));
        Product product = Product.create(brand, request.name(), request.description(),
                request.price(), request.category());
        return productRepository.save(product);
    }

    @Transactional
    public Product updateProduct(Long id, UpdateProductRequest request) {
        Product product = getProduct(id);
        product.update(request.name(), request.description(), request.price(), request.category());
        if (request.status() != null) {
            switch (request.status()) {
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

    public Page<Product> searchProducts(ProductSearchRequest request, Pageable pageable) {
        return productQueryRepository.search(
                request.keyword(), request.brandId(), request.category(),
                request.minPrice(), request.maxPrice(), pageable);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = getProduct(id);
        productRepository.delete(product);
    }

    public VariantDetailResponse getVariantDetail(Long variantId) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
        Product product = variant.getProduct();
        return new VariantDetailResponse(
                product.getId(),
                variant.getId(),
                product.getName(),
                variant.getSize(),
                variant.getColor(),
                variant.effectivePrice(),
                variant.getStockQuantity()
        );
    }

    @Transactional
    public ProductVariant reserveStock(Long variantId, int quantity) {
        ProductVariant variant = productVariantRepository.findWithLockById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
        variant.reserveStock(quantity);
        return variant;
    }

    @Transactional
    public ProductVariant releaseStock(Long variantId, int quantity) {
        ProductVariant variant = productVariantRepository.findWithLockById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
        variant.releaseStock(quantity);
        return variant;
    }
}
