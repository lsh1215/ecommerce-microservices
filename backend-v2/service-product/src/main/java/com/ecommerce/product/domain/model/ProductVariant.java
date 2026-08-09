package com.ecommerce.product.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.ProductErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_variant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    @Column(nullable = false)
    private String size;

    @Column(nullable = false)
    private String color;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private int stockQuantity;

    private BigDecimal price;

    static ProductVariant createInternal(Product product, String sku, String size,
                                         String color, int initialStock,
                                         BigDecimal priceOverride) {
        ProductVariant variant = new ProductVariant();
        variant.product = product;
        variant.sku = sku;
        variant.size = size;
        variant.color = color;
        variant.stockQuantity = initialStock;
        variant.price = priceOverride;
        return variant;
    }

    /**
     * Builds a detached, id-only {@link ProductVariant} for the async, Redis-only reserve
     * path where a successful admit must return a response without any DB read. All other
     * fields stay at their defaults ({@code null}/{@code 0}); {@code ProductVariantResponse}
     * tolerates that shape (it only reads getters, no lazy associations).
     */
    public static ProductVariant reference(Long id) {
        ProductVariant variant = new ProductVariant();
        try {
            java.lang.reflect.Field idField = BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(variant, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to build detached ProductVariant reference", e);
        }
        return variant;
    }

    public void reserveStock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK,
                    "Reserve quantity must be positive");
        }
        if (this.stockQuantity < quantity) {
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK,
                    String.format("Requested %d but only %d available", quantity, stockQuantity));
        }
        this.stockQuantity -= quantity;
    }

    public void releaseStock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ProductErrorCode.INVALID_VARIANT_OPERATION,
                    "Release quantity must be positive");
        }
        this.stockQuantity += quantity;
    }

    public boolean hasStock(int quantity) {
        return this.stockQuantity >= quantity;
    }

    public BigDecimal effectivePrice() {
        return price != null ? price : product.getPrice();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductVariant that = (ProductVariant) o;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
