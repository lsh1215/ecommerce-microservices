package com.ecommerce.inventory.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_variant_id", unique = true, nullable = false)
    private Long productVariantId;

    @Column(name = "quantity_available", nullable = false)
    private int quantityAvailable;

    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved;

    @Column(name = "quantity_sold", nullable = false)
    private int quantitySold;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    public static Inventory create(Long productVariantId) {
        Inventory inventory = new Inventory();
        inventory.productVariantId = productVariantId;
        inventory.quantityAvailable = 0;
        inventory.quantityReserved = 0;
        inventory.quantitySold = 0;
        return inventory;
    }

    public void reserve(int quantity) {
        this.quantityAvailable -= quantity;
        this.quantityReserved += quantity;
    }

    public void deduct(int quantity) {
        this.quantityReserved -= quantity;
        this.quantitySold += quantity;
    }

    public void release(int quantity) {
        this.quantityReserved -= quantity;
        this.quantityAvailable += quantity;
    }

    public void adjust(int delta) {
        this.quantityAvailable += delta;
    }
}
