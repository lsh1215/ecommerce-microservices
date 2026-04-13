package com.ecommerce.product.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.ProductErrorCode;
import com.ecommerce.product.api.dto.request.CreateBrandRequest;
import com.ecommerce.product.api.dto.request.UpdateBrandRequest;
import com.ecommerce.product.domain.model.Brand;
import com.ecommerce.product.domain.repository.BrandRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional
    public Brand createBrand(CreateBrandRequest request) {
        if (brandRepository.existsByName(request.name())) {
            throw new BusinessException(ProductErrorCode.DUPLICATE_BRAND);
        }
        Brand brand = Brand.create(request.name(), request.description(),
                request.logoUrl(), request.country());
        return brandRepository.save(brand);
    }

    @Transactional
    public Brand updateBrand(Long id, UpdateBrandRequest request) {
        Brand brand = getBrand(id);
        brand.update(request.description(), request.logoUrl(), request.country());
        return brand;
    }

    public Brand getBrand(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.BRAND_NOT_FOUND));
    }

    public List<Brand> getAllBrands() {
        return brandRepository.findAll();
    }
}
