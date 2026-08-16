package com.ecommerce.product.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.ProductErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /**
     * 이 옵션의 재고 차감을 어떤 방식으로 처리할지.
     *
     * <p>{@link StockContention#NORMAL}이면 {@code stockQuantity}를 조건부 UPDATE로 직접
     * 깎는다. 상위 등급이면 재고의 권위가 {@code stock_shard}(POPULAR) 또는
     * {@code stock_unit}(HOT)으로 옮겨가고, 이 컬럼은 표시용 합계가 된다.
     *
     * <p>기본값이 NORMAL인 이유는 상위 등급일수록 요청당 DB 작업이 늘기 때문이다. 경합이
     * 실제로 관측된 옵션만 올린다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "stock_contention", nullable = false, length = 16)
    private StockContention stockContention = StockContention.NORMAL;

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
