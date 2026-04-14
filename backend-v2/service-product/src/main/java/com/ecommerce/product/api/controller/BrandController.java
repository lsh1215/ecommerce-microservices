package com.ecommerce.product.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.product.api.dto.request.CreateBrandRequest;
import com.ecommerce.product.api.dto.request.UpdateBrandRequest;
import com.ecommerce.product.api.dto.response.BrandResponse;
import com.ecommerce.product.application.dto.CreateBrandCommand;
import com.ecommerce.product.application.dto.UpdateBrandCommand;
import com.ecommerce.product.application.service.BrandService;
import com.ecommerce.product.domain.model.Brand;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    public ApiResponse<List<BrandResponse>> getAllBrands() {
        List<BrandResponse> brands = brandService.getAllBrands().stream()
                .map(BrandResponse::from)
                .toList();
        return ApiResponse.ok(brands);
    }

    @GetMapping("/{id}")
    public ApiResponse<BrandResponse> getBrand(@PathVariable Long id) {
        Brand brand = brandService.getBrand(id);
        return ApiResponse.ok(BrandResponse.from(brand));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BrandResponse> createBrand(
            @Valid @RequestBody CreateBrandRequest request) {
        CreateBrandCommand command = new CreateBrandCommand(
                request.name(), request.description(), request.logoUrl(), request.country()
        );
        Brand brand = brandService.createBrand(command);
        return ApiResponse.created(BrandResponse.from(brand));
    }

    @PutMapping("/{id}")
    public ApiResponse<BrandResponse> updateBrand(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBrandRequest request) {
        UpdateBrandCommand command = new UpdateBrandCommand(
                request.name(), request.description(), request.logoUrl(), request.country()
        );
        Brand brand = brandService.updateBrand(id, command);
        return ApiResponse.ok(BrandResponse.from(brand));
    }
}
