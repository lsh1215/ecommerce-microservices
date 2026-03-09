package com.ecommerce.product.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", unique = true, nullable = false, length = 26, columnDefinition = "char(26)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(name = "slug", unique = true, nullable = false, length = 150)
    private String slug;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "era", length = 50)
    private String era;

    @Column(name = "base_price_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal basePriceAmount;

    @Column(name = "base_price_currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String basePriceCurrency;

    @Column(name = "price_usd", precision = 19, scale = 4)
    private BigDecimal priceUsd;

    @Column(name = "price_krw", precision = 19, scale = 4)
    private BigDecimal priceKrw;

    @Column(name = "price_jpy", precision = 19, scale = 4)
    private BigDecimal priceJpy;

    @Column(name = "fabric_weight_oz", precision = 4, scale = 1)
    private BigDecimal fabricWeightOz;

    @Column(name = "fabric_type", length = 50)
    private String fabricType;

    @Column(name = "fabric_weave", length = 50)
    private String fabricWeave;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductTranslation> translations = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) {
            this.publicId = UlidCreator.getUlid().toString();
        }
    }

    public static Product create(Brand brand, String slug, String category, String era,
                                 BigDecimal basePriceAmount, String basePriceCurrency,
                                 BigDecimal priceUsd, BigDecimal priceKrw, BigDecimal priceJpy,
                                 BigDecimal fabricWeightOz, String fabricType, String fabricWeave) {
        Product product = new Product();
        product.brand = brand;
        product.slug = slug;
        product.category = category;
        product.era = era;
        product.basePriceAmount = basePriceAmount;
        product.basePriceCurrency = basePriceCurrency;
        product.priceUsd = priceUsd;
        product.priceKrw = priceKrw;
        product.priceJpy = priceJpy;
        product.fabricWeightOz = fabricWeightOz;
        product.fabricType = fabricType;
        product.fabricWeave = fabricWeave;
        return product;
    }

    public void update(String slug, String category, String era,
                       BigDecimal basePriceAmount, String basePriceCurrency,
                       BigDecimal priceUsd, BigDecimal priceKrw, BigDecimal priceJpy,
                       BigDecimal fabricWeightOz, String fabricType, String fabricWeave) {
        this.slug = slug;
        this.category = category;
        this.era = era;
        this.basePriceAmount = basePriceAmount;
        this.basePriceCurrency = basePriceCurrency;
        this.priceUsd = priceUsd;
        this.priceKrw = priceKrw;
        this.priceJpy = priceJpy;
        this.fabricWeightOz = fabricWeightOz;
        this.fabricType = fabricType;
        this.fabricWeave = fabricWeave;
    }

    public static String generateSlug(String brandSlug, String productName) {
        String namePart = productName.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return brandSlug + "-" + namePart;
    }
}
