package com.ecommerce.product.application.port;

import com.ecommerce.product.application.dto.ProductListItemResult;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductQueryRepository {

    Page<ProductListItemResult> search(String keyword, Long brandId, String category,
                                       BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);
}
