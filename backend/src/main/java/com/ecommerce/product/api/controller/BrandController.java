package com.ecommerce.product.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.product.api.dto.request.CreateBrandRequest;
import com.ecommerce.product.api.dto.request.UpdateBrandRequest;
import com.ecommerce.product.api.dto.response.BrandResponse;
import com.ecommerce.product.application.service.BrandService;
import com.ecommerce.product.domain.model.Brand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BrandResponse>>> listBrands() {
        List<BrandResponse> brands = brandService.findAll().stream()
                .map(BrandResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(brands));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<BrandResponse>> getBySlug(@PathVariable String slug) {
        Brand brand = brandService.findBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(BrandResponse.from(brand)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BrandResponse>> createBrand(@Valid @RequestBody CreateBrandRequest request) {
        Brand brand = brandService.create(
                request.name(), request.slug(), request.countryOfOrigin(),
                request.styleCategory(), request.foundedYear(),
                request.description(), request.logoUrl()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(BrandResponse.from(brand)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> updateBrand(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBrandRequest request) {
        Brand brand = brandService.update(
                id, request.name(), request.slug(), request.countryOfOrigin(),
                request.styleCategory(), request.foundedYear(),
                request.description(), request.logoUrl()
        );
        return ResponseEntity.ok(ApiResponse.success(BrandResponse.from(brand)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBrand(@PathVariable Long id) {
        brandService.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
