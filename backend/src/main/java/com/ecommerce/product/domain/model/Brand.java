package com.ecommerce.product.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "brand")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brand extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", unique = true, nullable = false, length = 26, columnDefinition = "char(26)")
    private String publicId;

    @Column(name = "name", unique = true, nullable = false, length = 100)
    private String name;

    @Column(name = "slug", unique = true, nullable = false, length = 100)
    private String slug;

    @Column(name = "country_of_origin", length = 2, columnDefinition = "char(2)")
    private String countryOfOrigin;

    @Column(name = "style_category", length = 50)
    private String styleCategory;

    @Column(name = "founded_year")
    private Integer foundedYear;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) {
            this.publicId = UlidCreator.getUlid().toString();
        }
    }

    public static Brand create(String name, String slug, String countryOfOrigin,
                               String styleCategory, Integer foundedYear,
                               String description, String logoUrl) {
        Brand brand = new Brand();
        brand.name = name;
        brand.slug = (slug != null && !slug.isBlank()) ? slug : generateSlug(name);
        brand.countryOfOrigin = countryOfOrigin;
        brand.styleCategory = styleCategory;
        brand.foundedYear = foundedYear;
        brand.description = description;
        brand.logoUrl = logoUrl;
        return brand;
    }

    public void update(String name, String slug, String countryOfOrigin,
                       String styleCategory, Integer foundedYear,
                       String description, String logoUrl) {
        this.name = name;
        this.slug = (slug != null && !slug.isBlank()) ? slug : generateSlug(name);
        this.countryOfOrigin = countryOfOrigin;
        this.styleCategory = styleCategory;
        this.foundedYear = foundedYear;
        this.description = description;
        this.logoUrl = logoUrl;
    }

    public static String generateSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
