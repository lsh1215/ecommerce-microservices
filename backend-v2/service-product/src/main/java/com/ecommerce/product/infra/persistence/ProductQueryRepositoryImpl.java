package com.ecommerce.product.infra.persistence;

import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.QBrand;
import com.ecommerce.product.domain.model.QProduct;
import com.ecommerce.product.domain.model.QProductImage;
import com.ecommerce.product.domain.repository.ProductQueryRepository;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductQueryRepositoryImpl implements ProductQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Product> search(String keyword, Long brandId, String category,
                                BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        QProduct product = QProduct.product;
        QBrand brand = QBrand.brand;
        QProductImage image = QProductImage.productImage;

        BooleanBuilder builder = new BooleanBuilder();

        if (keyword != null && !keyword.isBlank()) {
            builder.and(product.name.containsIgnoreCase(keyword)
                    .or(product.description.containsIgnoreCase(keyword)));
        }
        if (brandId != null) {
            builder.and(product.brand.id.eq(brandId));
        }
        if (category != null && !category.isBlank()) {
            builder.and(product.category.eq(category));
        }
        if (minPrice != null) {
            builder.and(product.price.goe(minPrice));
        }
        if (maxPrice != null) {
            builder.and(product.price.loe(maxPrice));
        }

        JPAQuery<Product> query = queryFactory
                .selectFrom(product)
                .leftJoin(product.brand, brand).fetchJoin()
                .leftJoin(product.images, image).fetchJoin()
                .where(builder)
                .distinct();

        long total = query.fetchCount();

        List<Product> content = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return new PageImpl<>(content, pageable, total);
    }
}
