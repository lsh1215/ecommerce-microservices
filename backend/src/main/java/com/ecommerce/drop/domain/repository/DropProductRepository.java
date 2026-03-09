package com.ecommerce.drop.domain.repository;

import com.ecommerce.drop.domain.model.DropProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DropProductRepository extends JpaRepository<DropProduct, Long> {

    Optional<DropProduct> findByPublicId(String publicId);

    List<DropProduct> findByDropEventId(Long dropEventId);

    @Query("SELECT COALESCE(SUM(dp.allocatedQuantity), 0) FROM DropProduct dp WHERE dp.productVariantId = :variantId")
    int sumAllocatedQuantityByVariantId(@Param("variantId") Long variantId);
}
