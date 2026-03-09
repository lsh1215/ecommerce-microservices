package com.ecommerce.product.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.product.api.dto.request.CreateImageRequest;
import com.ecommerce.product.api.dto.request.CreateProductRequest;
import com.ecommerce.product.api.dto.request.CreateTranslationRequest;
import com.ecommerce.product.api.dto.request.CreateVariantRequest;
import com.ecommerce.product.api.dto.request.ProductSearchRequest;
import com.ecommerce.product.api.dto.request.UpdateProductRequest;
import com.ecommerce.product.api.dto.request.UpdateVariantRequest;
import com.ecommerce.product.api.dto.response.ProductDetailResponse;
import com.ecommerce.product.api.dto.response.ProductImageResponse;
import com.ecommerce.product.api.dto.response.ProductResponse;
import com.ecommerce.product.api.dto.response.ProductTranslationResponse;
import com.ecommerce.product.api.dto.response.ProductVariantResponse;
import com.ecommerce.product.application.service.ProductService;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.ProductImage;
import com.ecommerce.product.domain.model.ProductTranslation;
import com.ecommerce.product.domain.model.ProductVariant;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> listProducts(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String era,
            @RequestParam(required = false) String fabricType,
            @RequestParam(required = false) String fabricWeave,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        ProductSearchRequest request = new ProductSearchRequest(
                brandId, category, era, fabricType, fabricWeave,
                minPrice, maxPrice, page, size, sort, direction);

        Page<Product> result = productService.search(request);
        PageResponse<ProductResponse> pageResponse = PageResponse.from(result, ProductResponse::from);
        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getByPublicId(@PathVariable String publicId) {
        Product product = productService.findByPublicId(publicId);
        return ResponseEntity.ok(ApiResponse.success(ProductDetailResponse.from(product)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Product> result = productService.searchByKeyword(q, page, size);
        PageResponse<ProductResponse> pageResponse = PageResponse.from(result, ProductResponse::from);
        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        Product product = productService.create(
                request.brandId(), request.slug(), request.category(), request.era(),
                request.basePriceAmount(), request.basePriceCurrency(),
                request.priceUsd(), request.priceKrw(), request.priceJpy(),
                request.fabricWeightOz(), request.fabricType(), request.fabricWeave(),
                request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ProductResponse.from(product)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        Product product = productService.update(id,
                request.slug(), request.category(), request.era(),
                request.basePriceAmount(), request.basePriceCurrency(),
                request.priceUsd(), request.priceKrw(), request.priceJpy(),
                request.fabricWeightOz(), request.fabricType(), request.fabricWeave());
        return ResponseEntity.ok(ApiResponse.success(ProductResponse.from(product)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/{productId}/variants")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> addVariant(
            @PathVariable Long productId,
            @Valid @RequestBody CreateVariantRequest request) {
        ProductVariant variant = productService.addVariant(productId,
                request.sku(), request.sizeLabel(), request.colorName(), request.colorHex(),
                request.priceOverrideAmount(), request.priceOverrideCurrency(),
                request.measChestCm(), request.measShoulderCm(),
                request.measSleeveCm(), request.measBodyLengthCm(),
                request.measWaistCm(), request.measInseamCm(),
                request.measThighCm(), request.measHemCm());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ProductVariantResponse.from(variant)));
    }

    @PutMapping("/{productId}/variants/{variantId}")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> updateVariant(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @Valid @RequestBody UpdateVariantRequest request) {
        ProductVariant variant = productService.updateVariant(productId, variantId,
                request.sku(), request.sizeLabel(), request.colorName(), request.colorHex(),
                request.priceOverrideAmount(), request.priceOverrideCurrency(),
                request.measChestCm(), request.measShoulderCm(),
                request.measSleeveCm(), request.measBodyLengthCm(),
                request.measWaistCm(), request.measInseamCm(),
                request.measThighCm(), request.measHemCm());
        return ResponseEntity.ok(ApiResponse.success(ProductVariantResponse.from(variant)));
    }

    @DeleteMapping("/{productId}/variants/{variantId}")
    public ResponseEntity<ApiResponse<Void>> deleteVariant(
            @PathVariable Long productId,
            @PathVariable Long variantId) {
        productService.deleteVariant(productId, variantId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/{productId}/translations")
    public ResponseEntity<ApiResponse<ProductTranslationResponse>> addTranslation(
            @PathVariable Long productId,
            @Valid @RequestBody CreateTranslationRequest request) {
        ProductTranslation translation = productService.addTranslation(productId,
                request.locale(), request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ProductTranslationResponse.from(translation)));
    }

    @PostMapping("/{productId}/images")
    public ResponseEntity<ApiResponse<ProductImageResponse>> addImage(
            @PathVariable Long productId,
            @Valid @RequestBody CreateImageRequest request) {
        ProductImage image = productService.addImage(productId,
                request.url(), request.sortOrder(), request.isPrimary());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ProductImageResponse.from(image)));
    }

    @DeleteMapping("/{productId}/images/{imageId}")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        productService.deleteImage(productId, imageId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
