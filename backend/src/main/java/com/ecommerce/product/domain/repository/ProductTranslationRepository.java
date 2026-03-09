package com.ecommerce.product.domain.repository;

import com.ecommerce.product.domain.model.ProductTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductTranslationRepository extends JpaRepository<ProductTranslation, Long> {

    boolean existsByProductIdAndLocale(Long productId, String locale);
}
