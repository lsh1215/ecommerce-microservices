package com.ecommerce.drop.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ErrorCode;
import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "drop_product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DropProduct extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", unique = true, nullable = false, length = 26, columnDefinition = "char(26)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drop_event_id", nullable = false)
    private DropEvent dropEvent;

    @Column(name = "product_variant_id", nullable = false)
    private Long productVariantId;

    @Column(name = "allocated_quantity", nullable = false)
    private int allocatedQuantity;

    @Column(name = "sold_quantity", nullable = false)
    private int soldQuantity;

    @Column(name = "drop_price_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal dropPriceAmount;

    @Column(name = "drop_price_currency", nullable = false, length = 3)
    private String dropPriceCurrency;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) {
            this.publicId = UlidCreator.getUlid().toString();
        }
    }

    public static DropProduct create(DropEvent dropEvent, Long productVariantId,
                                     int allocatedQuantity, BigDecimal dropPriceAmount,
                                     String dropPriceCurrency) {
        DropProduct product = new DropProduct();
        product.dropEvent = dropEvent;
        product.productVariantId = productVariantId;
        product.allocatedQuantity = allocatedQuantity;
        product.soldQuantity = 0;
        product.dropPriceAmount = dropPriceAmount;
        product.dropPriceCurrency = dropPriceCurrency;
        return product;
    }

    public void sell(int quantity) {
        if (this.soldQuantity + quantity > this.allocatedQuantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                    "Cannot sell " + quantity + " units: only " +
                            (this.allocatedQuantity - this.soldQuantity) + " remaining");
        }
        this.soldQuantity += quantity;
    }
}
