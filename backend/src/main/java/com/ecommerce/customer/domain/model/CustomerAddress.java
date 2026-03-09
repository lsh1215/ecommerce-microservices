package com.ecommerce.customer.domain.model;

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

@Entity
@Table(name = "customer_address")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerAddress extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", unique = true, nullable = false, length = 26, columnDefinition = "char(26)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "label", length = 50)
    private String label;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "street", nullable = false)
    private String street;

    @Column(name = "detail")
    private String detail;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state_province", length = 100)
    private String stateProvince;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(name = "country", nullable = false, length = 2, columnDefinition = "char(2)")
    private String country;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) {
            this.publicId = UlidCreator.getUlid().toString();
        }
    }

    public static CustomerAddress create(Customer customer, String label, String recipientName,
                                          String phone, String street, String detail, String city,
                                          String stateProvince, String postalCode, String country,
                                          boolean isDefault) {
        CustomerAddress address = new CustomerAddress();
        address.customer = customer;
        address.label = label;
        address.recipientName = recipientName;
        address.phone = phone;
        address.street = street;
        address.detail = detail;
        address.city = city;
        address.stateProvince = stateProvince;
        address.postalCode = postalCode;
        address.country = country;
        address.isDefault = isDefault;
        return address;
    }

    public void update(String label, String recipientName, String phone, String street,
                       String detail, String city, String stateProvince, String postalCode,
                       String country, boolean isDefault) {
        this.label = label;
        this.recipientName = recipientName;
        this.phone = phone;
        this.street = street;
        this.detail = detail;
        this.city = city;
        this.stateProvince = stateProvince;
        this.postalCode = postalCode;
        this.country = country;
        this.isDefault = isDefault;
    }

    public void clearDefault() {
        this.isDefault = false;
    }
}
