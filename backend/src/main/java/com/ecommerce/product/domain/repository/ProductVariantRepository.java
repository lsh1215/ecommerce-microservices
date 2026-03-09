package com.ecommerce.product.domain.repository;

import com.ecommerce.product.domain.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    Optional<ProductVariant> findByPublicId(String publicId);

    boolean existsBySku(String sku);
}
