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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_translation", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"product_id", "locale"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductTranslation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "locale", nullable = false, length = 2, columnDefinition = "char(2)")
    private String locale;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    public static ProductTranslation create(Product product, String locale, String name, String description) {
        ProductTranslation translation = new ProductTranslation();
        translation.product = product;
        translation.locale = locale;
        translation.name = name;
        translation.description = description;
        return translation;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
