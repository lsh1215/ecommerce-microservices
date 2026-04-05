package com.ecommerce.product.application.service;

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
        throw new UnsupportedOperationException("implement me");
    }

    @Transactional
    public Brand updateBrand(Long id, UpdateBrandRequest request) {
        throw new UnsupportedOperationException("implement me");
    }

    public Brand getBrand(Long id) {
        throw new UnsupportedOperationException("implement me");
    }

    public List<Brand> getAllBrands() {
        throw new UnsupportedOperationException("implement me");
    }
}
