package com.ecommerce.product.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.ProductErrorCode;
import com.ecommerce.product.application.dto.CreateBrandCommand;
import com.ecommerce.product.application.dto.UpdateBrandCommand;
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

    /**
     * 새로운 헤리티지 웨어 브랜드를 등록한다.
     * 애플리케이션 레벨에서 브랜드명 중복 제약을 강제한다.
     */
    @Transactional
    public Brand createBrand(CreateBrandCommand command) {
        // 가드: 브랜드명 중복 방지
        if (brandRepository.existsByName(command.name())) {
            throw new BusinessException(ProductErrorCode.DUPLICATE_BRAND);
        }
        Brand brand = Brand.create(command.name(), command.description(),
                command.logoUrl(), command.country());
        return brandRepository.save(brand);
    }

    @Transactional
    public Brand updateBrand(Long id, UpdateBrandCommand command) {
        Brand brand = getBrand(id);
        brand.update(command.description(), command.logoUrl(), command.country());
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
