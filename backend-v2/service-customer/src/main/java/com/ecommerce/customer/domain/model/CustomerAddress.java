package com.ecommerce.customer.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.customer.CustomerErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "customer_address")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerAddress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AddressLabel label;

    @Column(nullable = false)
    private String recipientName;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String zipCode;

    @Column(nullable = false)
    private String address1;

    private String address2;

    @Column(nullable = false)
    private boolean isDefault;

    public static CustomerAddress create(Customer customer, AddressLabel label,
                                         String recipientName, String phone,
                                         String zipCode, String address1,
                                         String address2, boolean isDefault) {
        if (customer == null) {
            throw new BusinessException(CustomerErrorCode.INVALID_ADDRESS_DATA,
                    "Customer must not be null");
        }
        if (label == null) {
            throw new BusinessException(CustomerErrorCode.INVALID_ADDRESS_DATA,
                    "Label must not be null");
        }
        if (recipientName == null || recipientName.isBlank()) {
            throw new BusinessException(CustomerErrorCode.INVALID_ADDRESS_DATA,
                    "Recipient name must not be blank");
        }

        CustomerAddress address = new CustomerAddress();
        address.customer = customer;
        address.label = label;
        address.recipientName = recipientName;
        address.phone = phone;
        address.zipCode = zipCode;
        address.address1 = address1;
        address.address2 = address2;
        address.isDefault = isDefault;
        return address;
    }

    public void markAsDefault() {
        this.isDefault = true;
    }

    public void unmarkDefault() {
        this.isDefault = false;
    }

    public void update(AddressLabel label, String recipientName, String phone,
                       String zipCode, String address1, String address2) {
        if (label != null) {
            this.label = label;
        }
        if (recipientName != null && !recipientName.isBlank()) {
            this.recipientName = recipientName;
        }
        if (phone != null) {
            this.phone = phone;
        }
        if (zipCode != null) {
            this.zipCode = zipCode;
        }
        if (address1 != null) {
            this.address1 = address1;
        }
        if (address2 != null) {
            this.address2 = address2;
        }
    }
}
