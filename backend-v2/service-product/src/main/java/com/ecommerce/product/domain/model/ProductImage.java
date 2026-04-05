package com.ecommerce.product.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean isPrimary;

    static ProductImage createInternal(Product product, String url, int sortOrder,
                                       boolean isPrimary) {
        ProductImage image = new ProductImage();
        image.product = product;
        image.url = url;
        image.sortOrder = sortOrder;
        image.isPrimary = isPrimary;
        return image;
    }

    void clearPrimary() {
        this.isPrimary = false;
    }
}
