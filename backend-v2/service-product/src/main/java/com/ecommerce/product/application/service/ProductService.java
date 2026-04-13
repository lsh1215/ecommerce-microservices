package com.ecommerce.product.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.ProductErrorCode;
import com.ecommerce.product.api.dto.request.CreateProductRequest;
import com.ecommerce.product.api.dto.request.UpdateProductRequest;
import com.ecommerce.product.api.dto.request.ProductSearchRequest;
import com.ecommerce.product.api.dto.response.VariantDetailResponse;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.ProductVariant;
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

    @Transactional
    public Product createProduct(CreateProductRequest request) {
        throw new UnsupportedOperationException("implement me");
    }

    @Transactional
    public Product updateProduct(Long id, UpdateProductRequest request) {
        throw new UnsupportedOperationException("implement me");
    }

    public Product getProduct(Long id) {
        throw new UnsupportedOperationException("implement me");
    }

    public Page<Product> searchProducts(ProductSearchRequest request, Pageable pageable) {
        throw new UnsupportedOperationException("implement me");
    }

    @Transactional
    public void deleteProduct(Long id) {
        throw new UnsupportedOperationException("implement me");
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
