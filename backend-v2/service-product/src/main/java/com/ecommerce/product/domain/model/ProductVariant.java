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
            throw new IllegalArgumentException("Release quantity must be positive");
        }
        this.stockQuantity += quantity;
    }

    public boolean hasStock(int quantity) {
        return this.stockQuantity >= quantity;
    }

    public BigDecimal effectivePrice() {
        return price != null ? price : product.getPrice();
    }
}
