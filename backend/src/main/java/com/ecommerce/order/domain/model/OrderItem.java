package com.ecommerce.order.domain.model;

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
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter(AccessLevel.PACKAGE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Orders order;

    @Column(name = "product_variant_id", nullable = false)
    private Long productVariantId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "brand_name", nullable = false, length = 100)
    private String brandName;

    @Column(name = "unit_price_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPriceAmount;

    @Column(name = "unit_price_currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String unitPriceCurrency;

    @Column(name = "size_label", nullable = false, length = 20)
    private String sizeLabel;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    public static OrderItem create(Long productVariantId, int quantity,
                                   String productName, String brandName,
                                   BigDecimal unitPriceAmount, String unitPriceCurrency,
                                   String sizeLabel, String sku) {
        OrderItem item = new OrderItem();
        item.productVariantId = productVariantId;
        item.quantity = quantity;
        item.productName = productName;
        item.brandName = brandName;
        item.unitPriceAmount = unitPriceAmount;
        item.unitPriceCurrency = unitPriceCurrency;
        item.sizeLabel = sizeLabel;
        item.sku = sku;
        return item;
    }

    public BigDecimal subtotal() {
        return unitPriceAmount.multiply(BigDecimal.valueOf(quantity));
    }
}
