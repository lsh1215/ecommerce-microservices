package com.ecommerce.product.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Lob
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status = ProductStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    private Brand brand;

    private String category;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    public static Product create(Brand brand, String name, String description,
                                 BigDecimal price, String category) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name must not be blank");
        }
        if (brand == null) {
            throw new IllegalArgumentException("Brand must not be null");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price must be >= 0");
        }
        Product product = new Product();
        product.brand = brand;
        product.name = name.trim();
        product.description = description;
        product.price = price;
        product.category = category;
        return product;
    }

    public void update(String name, String description, BigDecimal price, String category) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
        if (description != null) {
            this.description = description;
        }
        if (price != null && price.compareTo(BigDecimal.ZERO) >= 0) {
            this.price = price;
        }
        if (category != null) {
            this.category = category;
        }
    }

    public void activate() {
        this.status = ProductStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = ProductStatus.INACTIVE;
    }

    public ProductVariant addVariant(String sku, String size, String color,
                                     int initialStock, BigDecimal priceOverride) {
        ProductVariant variant = ProductVariant.createInternal(
                this, sku, size, color, initialStock, priceOverride);
        this.variants.add(variant);
        return variant;
    }

    public ProductImage addImage(String url, int sortOrder, boolean isPrimary) {
        if (isPrimary) {
            this.images.forEach(img -> img.clearPrimary());
        }
        ProductImage image = ProductImage.createInternal(this, url, sortOrder, isPrimary);
        this.images.add(image);
        return image;
    }

    public ProductVariant findVariant(Long variantId) {
        return this.variants.stream()
                .filter(v -> v.getId() != null && v.getId().equals(variantId))
                .findFirst()
                .orElse(null);
    }
}
