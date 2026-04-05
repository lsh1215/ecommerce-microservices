package com.ecommerce.product.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "brand")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brand extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    private String logoUrl;

    @Column(length = 2)
    private String country;

    public static Brand create(String name, String description, String logoUrl, String country) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Brand name must not be blank");
        }
        Brand brand = new Brand();
        brand.name = name.trim();
        brand.description = description;
        brand.logoUrl = logoUrl;
        brand.country = country;
        return brand;
    }

    public void update(String description, String logoUrl, String country) {
        this.description = description;
        this.logoUrl = logoUrl;
        this.country = country;
    }
}
