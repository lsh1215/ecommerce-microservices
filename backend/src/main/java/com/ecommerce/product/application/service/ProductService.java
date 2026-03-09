package com.ecommerce.product.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.product.api.dto.request.ProductSearchRequest;
import com.ecommerce.product.domain.model.Brand;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.ProductImage;
import com.ecommerce.product.domain.model.ProductTranslation;
import com.ecommerce.product.domain.model.ProductVariant;
import org.hibernate.Hibernate;
import com.ecommerce.product.domain.repository.BrandRepository;
import com.ecommerce.product.domain.repository.ProductImageRepository;
import com.ecommerce.product.domain.repository.ProductRepository;
import com.ecommerce.product.domain.repository.ProductTranslationRepository;
import com.ecommerce.product.domain.repository.ProductVariantRepository;
import com.ecommerce.product.infra.persistence.ProductQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductTranslationRepository productTranslationRepository;
    private final ProductImageRepository productImageRepository;
    private final BrandRepository brandRepository;
    private final ProductQueryRepository productQueryRepository;

    @Transactional
    public Product create(Long brandId, String slug, String category, String era,
                          BigDecimal basePriceAmount, String basePriceCurrency,
                          BigDecimal priceUsd, BigDecimal priceKrw, BigDecimal priceJpy,
                          BigDecimal fabricWeightOz, String fabricType, String fabricWeave,
                          String name) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new EntityNotFoundException("Brand", brandId));

        String resolvedSlug = (slug != null && !slug.isBlank()) ? slug : Product.generateSlug(brand.getSlug(), name);
        if (productRepository.existsBySlug(resolvedSlug)) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENTITY, "Product slug already exists: " + resolvedSlug);
        }

        Product product = Product.create(brand, resolvedSlug, category, era,
                basePriceAmount, basePriceCurrency, priceUsd, priceKrw, priceJpy,
                fabricWeightOz, fabricType, fabricWeave);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product findByPublicId(String publicId) {
        Product product = productRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Product", publicId));
        Hibernate.initialize(product.getVariants());
        Hibernate.initialize(product.getTranslations());
        Hibernate.initialize(product.getImages());
        return product;
    }

    @Transactional(readOnly = true)
    public Page<Product> search(ProductSearchRequest request) {
        Pageable pageable = PageRequest.of(request.page(), request.size());
        return productQueryRepository.search(request, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> searchByKeyword(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return productRepository.searchByKeyword(query, pageable);
    }

    @Transactional
    public Product update(Long id, String slug, String category, String era,
                          BigDecimal basePriceAmount, String basePriceCurrency,
                          BigDecimal priceUsd, BigDecimal priceKrw, BigDecimal priceJpy,
                          BigDecimal fabricWeightOz, String fabricType, String fabricWeave) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product", id));

        if (slug != null && !slug.isBlank() && !product.getSlug().equals(slug)) {
            if (productRepository.existsBySlug(slug)) {
                throw new BusinessException(ErrorCode.DUPLICATE_ENTITY, "Product slug already exists: " + slug);
            }
        }

        String resolvedSlug = (slug != null && !slug.isBlank()) ? slug : product.getSlug();
        product.update(resolvedSlug, category, era, basePriceAmount, basePriceCurrency,
                priceUsd, priceKrw, priceJpy, fabricWeightOz, fabricType, fabricWeave);
        Hibernate.initialize(product.getBrand());
        return productRepository.save(product);
    }

    @Transactional
    public void delete(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product", id));
        productRepository.delete(product);
    }

    @Transactional
    public ProductVariant addVariant(Long productId, String sku, String sizeLabel,
                                     String colorName, String colorHex,
                                     BigDecimal priceOverrideAmount, String priceOverrideCurrency,
                                     BigDecimal measChestCm, BigDecimal measShoulderCm,
                                     BigDecimal measSleeveCm, BigDecimal measBodyLengthCm,
                                     BigDecimal measWaistCm, BigDecimal measInseamCm,
                                     BigDecimal measThighCm, BigDecimal measHemCm) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product", productId));

        if (productVariantRepository.existsBySku(sku)) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENTITY, "SKU already exists: " + sku);
        }

        ProductVariant variant = ProductVariant.create(product, sku, sizeLabel, colorName, colorHex,
                priceOverrideAmount, priceOverrideCurrency,
                measChestCm, measShoulderCm, measSleeveCm, measBodyLengthCm,
                measWaistCm, measInseamCm, measThighCm, measHemCm);
        return productVariantRepository.save(variant);
    }

    @Transactional
    public ProductVariant updateVariant(Long productId, Long variantId,
                                        String sku, String sizeLabel,
                                        String colorName, String colorHex,
                                        BigDecimal priceOverrideAmount, String priceOverrideCurrency,
                                        BigDecimal measChestCm, BigDecimal measShoulderCm,
                                        BigDecimal measSleeveCm, BigDecimal measBodyLengthCm,
                                        BigDecimal measWaistCm, BigDecimal measInseamCm,
                                        BigDecimal measThighCm, BigDecimal measHemCm) {
        if (!productRepository.existsById(productId)) {
            throw new EntityNotFoundException("Product", productId);
        }

        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new EntityNotFoundException("ProductVariant", variantId));

        if (!variant.getSku().equals(sku) && productVariantRepository.existsBySku(sku)) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENTITY, "SKU already exists: " + sku);
        }

        variant.update(sku, sizeLabel, colorName, colorHex,
                priceOverrideAmount, priceOverrideCurrency,
                measChestCm, measShoulderCm, measSleeveCm, measBodyLengthCm,
                measWaistCm, measInseamCm, measThighCm, measHemCm);
        return productVariantRepository.save(variant);
    }

    @Transactional
    public void deleteVariant(Long productId, Long variantId) {
        if (!productRepository.existsById(productId)) {
            throw new EntityNotFoundException("Product", productId);
        }
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new EntityNotFoundException("ProductVariant", variantId));
        productVariantRepository.delete(variant);
    }

    @Transactional
    public ProductTranslation addTranslation(Long productId, String locale, String name, String description) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product", productId));

        if (productTranslationRepository.existsByProductIdAndLocale(productId, locale)) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENTITY,
                    "Translation already exists for locale: " + locale);
        }

        ProductTranslation translation = ProductTranslation.create(product, locale, name, description);
        return productTranslationRepository.save(translation);
    }

    @Transactional
    public ProductImage addImage(Long productId, String url, Short sortOrder, Boolean isPrimary) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product", productId));

        if (Boolean.TRUE.equals(isPrimary)) {
            productImageRepository.clearPrimaryByProductId(productId);
        }

        ProductImage image = ProductImage.create(product, url, sortOrder, isPrimary);
        return productImageRepository.save(image);
    }

    @Transactional
    public void deleteImage(Long productId, Long imageId) {
        if (!productRepository.existsById(productId)) {
            throw new EntityNotFoundException("Product", productId);
        }
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new EntityNotFoundException("ProductImage", imageId));
        productImageRepository.delete(image);
    }
}
