package com.ecommerce.product.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.product.api.dto.request.CreateProductRequest;
import com.ecommerce.product.api.dto.request.UpdateProductRequest;
import com.ecommerce.product.api.dto.response.ProductDetailResponse;
import com.ecommerce.product.api.dto.response.ProductResponse;
import com.ecommerce.product.application.dto.CreateProductCommand;
import com.ecommerce.product.application.dto.ProductSearchCommand;
import com.ecommerce.product.application.dto.UpdateProductCommand;
import com.ecommerce.product.application.service.ProductService;
import com.ecommerce.product.domain.model.Product;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            Pageable pageable) {
        ProductSearchCommand command = new ProductSearchCommand(
                keyword, brandId, category, minPrice, maxPrice);
        Page<Product> products = productService.searchProducts(command, pageable);
        Page<ProductResponse> responsePage = products.map(ProductResponse::from);
        return ApiResponse.ok(PageResponse.from(responsePage));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductDetailResponse> getProduct(@PathVariable Long id) {
        Product product = productService.getProduct(id);
        return ApiResponse.ok(ProductDetailResponse.from(product));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductDetailResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        CreateProductCommand command = new CreateProductCommand(
                request.name(), request.description(), request.price(),
                request.brandId(), request.category()
        );
        Product product = productService.createProduct(command);
        return ApiResponse.created(ProductDetailResponse.from(product));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductDetailResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        UpdateProductCommand command = new UpdateProductCommand(
                request.name(), request.description(), request.price(),
                request.category(), request.status()
        );
        Product product = productService.updateProduct(id, command);
        return ApiResponse.ok(ProductDetailResponse.from(product));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ApiResponse.ok(null);
    }
}
