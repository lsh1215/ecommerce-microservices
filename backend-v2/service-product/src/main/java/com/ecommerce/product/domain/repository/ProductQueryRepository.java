package com.ecommerce.product.domain.repository;

import com.ecommerce.product.domain.model.Product;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductQueryRepository {

    Page<Product> search(String keyword, Long brandId, String category,
                         BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);
}
