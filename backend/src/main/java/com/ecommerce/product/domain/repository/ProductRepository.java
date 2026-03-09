package com.ecommerce.product.domain.repository;

import com.ecommerce.product.domain.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p JOIN FETCH p.brand WHERE p.publicId = :publicId")
    Optional<Product> findByPublicId(@Param("publicId") String publicId);

    boolean existsBySlug(String slug);

    @Query(value = "SELECT DISTINCT p FROM Product p JOIN FETCH p.brand LEFT JOIN p.translations t " +
            "WHERE t.name LIKE %:query% OR p.slug LIKE %:query%",
            countQuery = "SELECT COUNT(DISTINCT p) FROM Product p LEFT JOIN p.translations t " +
                    "WHERE t.name LIKE %:query% OR p.slug LIKE %:query%")
    Page<Product> searchByKeyword(@Param("query") String query, Pageable pageable);
}
