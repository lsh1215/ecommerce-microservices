package com.ecommerce.customer.domain.model;

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
@Table(name = "customer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", unique = true, nullable = false, length = 26, columnDefinition = "char(26)")
    private String publicId;

    @Column(name = "email", unique = true, nullable = false, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "preferred_currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String preferredCurrency;

    @Column(name = "preferred_locale", nullable = false, length = 2, columnDefinition = "char(2)")
    private String preferredLocale;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) {
            this.publicId = UlidCreator.getUlid().toString();
        }
    }

    public static Customer create(String email, String passwordHash, String name) {
        Customer customer = new Customer();
        customer.email = email;
        customer.passwordHash = passwordHash;
        customer.name = name;
        customer.preferredCurrency = "USD";
        customer.preferredLocale = "en";
        customer.role = "CUSTOMER";
        return customer;
    }
}
