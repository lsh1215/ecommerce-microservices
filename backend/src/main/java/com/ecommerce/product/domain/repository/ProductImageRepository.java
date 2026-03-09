package com.ecommerce.product.domain.repository;

import com.ecommerce.product.domain.model.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductImage i SET i.isPrimary = false WHERE i.product.id = :productId AND i.isPrimary = true")
    void clearPrimaryByProductId(@Param("productId") Long productId);
}
