package com.ecommerce.product.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary;

    public static ProductImage create(Product product, String url, Short sortOrder, Boolean isPrimary) {
        ProductImage image = new ProductImage();
        image.product = product;
        image.url = url;
        image.sortOrder = sortOrder != null ? sortOrder : 0;
        image.isPrimary = isPrimary != null ? isPrimary : false;
        return image;
    }

    public void markAsPrimary(boolean primary) {
        this.isPrimary = primary;
    }
}
