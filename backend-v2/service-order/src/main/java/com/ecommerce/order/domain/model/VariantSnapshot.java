package com.ecommerce.order.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class VariantSnapshot {

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long productVariantId;

    @Column(nullable = false)
    private String productName;

    private String size;

    private String color;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    public VariantSnapshot(Long productId, Long productVariantId, String productName,
                           String size, String color, BigDecimal unitPrice) {
        this.productId = productId;
        this.productVariantId = productVariantId;
        this.productName = productName;
        this.size = size;
        this.color = color;
        this.unitPrice = unitPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VariantSnapshot that)) return false;
        return Objects.equals(productId, that.productId)
                && Objects.equals(productVariantId, that.productVariantId)
                && Objects.equals(productName, that.productName)
                && Objects.equals(size, that.size)
                && Objects.equals(color, that.color)
                && Objects.equals(unitPrice, that.unitPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, productVariantId, productName, size, color, unitPrice);
    }
}
