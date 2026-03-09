package com.ecommerce.product.domain.model;

import com.ecommerce.common.entity.BaseEntity;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "product_variant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVariant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", unique = true, nullable = false, length = 26, columnDefinition = "char(26)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sku", unique = true, nullable = false, length = 100)
    private String sku;

    @Column(name = "size_label", nullable = false, length = 20)
    private String sizeLabel;

    @Column(name = "color_name", length = 50)
    private String colorName;

    @Column(name = "color_hex", length = 7, columnDefinition = "char(7)")
    private String colorHex;

    @Column(name = "price_override_amount", precision = 19, scale = 4)
    private BigDecimal priceOverrideAmount;

    @Column(name = "price_override_currency", length = 3, columnDefinition = "char(3)")
    private String priceOverrideCurrency;

    @Column(name = "meas_chest_cm", precision = 5, scale = 1)
    private BigDecimal measChestCm;

    @Column(name = "meas_shoulder_cm", precision = 5, scale = 1)
    private BigDecimal measShoulderCm;

    @Column(name = "meas_sleeve_cm", precision = 5, scale = 1)
    private BigDecimal measSleeveCm;

    @Column(name = "meas_body_length_cm", precision = 5, scale = 1)
    private BigDecimal measBodyLengthCm;

    @Column(name = "meas_waist_cm", precision = 5, scale = 1)
    private BigDecimal measWaistCm;

    @Column(name = "meas_inseam_cm", precision = 5, scale = 1)
    private BigDecimal measInseamCm;

    @Column(name = "meas_thigh_cm", precision = 5, scale = 1)
    private BigDecimal measThighCm;

    @Column(name = "meas_hem_cm", precision = 5, scale = 1)
    private BigDecimal measHemCm;

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) {
            this.publicId = UlidCreator.getUlid().toString();
        }
    }

    public static ProductVariant create(Product product, String sku, String sizeLabel,
                                        String colorName, String colorHex,
                                        BigDecimal priceOverrideAmount, String priceOverrideCurrency,
                                        BigDecimal measChestCm, BigDecimal measShoulderCm,
                                        BigDecimal measSleeveCm, BigDecimal measBodyLengthCm,
                                        BigDecimal measWaistCm, BigDecimal measInseamCm,
                                        BigDecimal measThighCm, BigDecimal measHemCm) {
        ProductVariant variant = new ProductVariant();
        variant.product = product;
        variant.sku = sku;
        variant.sizeLabel = sizeLabel;
        variant.colorName = colorName;
        variant.colorHex = colorHex;
        variant.priceOverrideAmount = priceOverrideAmount;
        variant.priceOverrideCurrency = priceOverrideCurrency;
        variant.measChestCm = measChestCm;
        variant.measShoulderCm = measShoulderCm;
        variant.measSleeveCm = measSleeveCm;
        variant.measBodyLengthCm = measBodyLengthCm;
        variant.measWaistCm = measWaistCm;
        variant.measInseamCm = measInseamCm;
        variant.measThighCm = measThighCm;
        variant.measHemCm = measHemCm;
        return variant;
    }

    public void update(String sku, String sizeLabel, String colorName, String colorHex,
                       BigDecimal priceOverrideAmount, String priceOverrideCurrency,
                       BigDecimal measChestCm, BigDecimal measShoulderCm,
                       BigDecimal measSleeveCm, BigDecimal measBodyLengthCm,
                       BigDecimal measWaistCm, BigDecimal measInseamCm,
                       BigDecimal measThighCm, BigDecimal measHemCm) {
        this.sku = sku;
        this.sizeLabel = sizeLabel;
        this.colorName = colorName;
        this.colorHex = colorHex;
        this.priceOverrideAmount = priceOverrideAmount;
        this.priceOverrideCurrency = priceOverrideCurrency;
        this.measChestCm = measChestCm;
        this.measShoulderCm = measShoulderCm;
        this.measSleeveCm = measSleeveCm;
        this.measBodyLengthCm = measBodyLengthCm;
        this.measWaistCm = measWaistCm;
        this.measInseamCm = measInseamCm;
        this.measThighCm = measThighCm;
        this.measHemCm = measHemCm;
    }
}
