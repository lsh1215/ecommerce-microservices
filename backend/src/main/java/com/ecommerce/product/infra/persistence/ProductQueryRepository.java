package com.ecommerce.product.infra.persistence;

import com.ecommerce.product.api.dto.request.ProductSearchRequest;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.QProduct;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ProductQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Page<Product> search(ProductSearchRequest request, Pageable pageable) {
        QProduct product = QProduct.product;

        BooleanBuilder builder = new BooleanBuilder();

        if (request.brandId() != null) {
            builder.and(product.brand.id.eq(request.brandId()));
        }
        if (request.category() != null && !request.category().isBlank()) {
            builder.and(product.category.eq(request.category()));
        }
        if (request.era() != null && !request.era().isBlank()) {
            builder.and(product.era.eq(request.era()));
        }
        if (request.fabricType() != null && !request.fabricType().isBlank()) {
            builder.and(product.fabricType.eq(request.fabricType()));
        }
        if (request.fabricWeave() != null && !request.fabricWeave().isBlank()) {
            builder.and(product.fabricWeave.eq(request.fabricWeave()));
        }
        if (request.minPrice() != null) {
            builder.and(product.basePriceAmount.goe(request.minPrice()));
        }
        if (request.maxPrice() != null) {
            builder.and(product.basePriceAmount.loe(request.maxPrice()));
        }

        OrderSpecifier<?> orderSpecifier = resolveOrder(product, request.sort(), request.direction());

        List<Product> content = queryFactory.selectFrom(product)
                .join(product.brand).fetchJoin()
                .where(builder)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(orderSpecifier)
                .fetch();

        long total = queryFactory.select(product.count())
                .from(product)
                .where(builder)
                .fetchOne();

        return new PageImpl<>(content, pageable, total);
    }

    private OrderSpecifier<?> resolveOrder(QProduct product, String sort, String direction) {
        boolean asc = "asc".equalsIgnoreCase(direction);
        if ("basePrice".equals(sort)) {
            return asc ? product.basePriceAmount.asc() : product.basePriceAmount.desc();
        }
        return asc ? product.createdAt.asc() : product.createdAt.desc();
    }
}
