package com.ecommerce.order.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Order order;

    @Embedded
    private VariantSnapshot variantSnapshot;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    public static OrderItem create(VariantSnapshot snapshot, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        OrderItem item = new OrderItem();
        item.variantSnapshot = snapshot;
        item.quantity = quantity;
        item.totalPrice = snapshot.getUnitPrice().multiply(BigDecimal.valueOf(quantity));
        return item;
    }

    void setOrder(Order order) {
        this.order = order;
    }
}
