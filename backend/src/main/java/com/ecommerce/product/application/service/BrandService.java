package com.ecommerce.product.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.product.domain.model.Brand;
import com.ecommerce.product.domain.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional
    public Brand create(String name, String slug, String countryOfOrigin,
                        String styleCategory, Integer foundedYear,
                        String description, String logoUrl) {
        if (brandRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENTITY, "Brand name already exists: " + name);
        }
        String resolvedSlug = (slug != null && !slug.isBlank()) ? slug : Brand.generateSlug(name);
        if (brandRepository.existsBySlug(resolvedSlug)) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENTITY, "Brand slug already exists: " + resolvedSlug);
        }
        Brand brand = Brand.create(name, slug, countryOfOrigin, styleCategory, foundedYear, description, logoUrl);
        return brandRepository.save(brand);
    }

    @Transactional(readOnly = true)
    public Brand findBySlug(String slug) {
        return brandRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Brand", slug));
    }

    @Transactional(readOnly = true)
    public List<Brand> findAll() {
        return brandRepository.findAll();
    }

    @Transactional
    public Brand update(Long id, String name, String slug, String countryOfOrigin,
                        String styleCategory, Integer foundedYear,
                        String description, String logoUrl) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Brand", id));

        if (!brand.getName().equals(name) && brandRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENTITY, "Brand name already exists: " + name);
        }
        String resolvedSlug = (slug != null && !slug.isBlank()) ? slug : Brand.generateSlug(name);
        if (!brand.getSlug().equals(resolvedSlug) && brandRepository.existsBySlug(resolvedSlug)) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENTITY, "Brand slug already exists: " + resolvedSlug);
        }

        brand.update(name, slug, countryOfOrigin, styleCategory, foundedYear, description, logoUrl);
        return brandRepository.save(brand);
    }

    @Transactional
    public void delete(Long id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Brand", id));
        brandRepository.delete(brand);
    }
}
