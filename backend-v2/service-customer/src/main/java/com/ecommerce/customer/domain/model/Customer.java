package com.ecommerce.customer.domain.model;

import com.ecommerce.common.entity.BaseEntity;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.customer.CustomerErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;

@Entity
@Table(name = "customer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends BaseEntity {

    @Column(name = "email", unique = true, nullable = false)
    private Email email;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    private String phone;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerAddress> addresses = new ArrayList<>();

    public static Customer create(Email email, String rawPassword, String name, String phone) {
        if (email == null) {
            throw new BusinessException(CustomerErrorCode.INVALID_CUSTOMER_DATA,
                    "Email must not be null");
        }
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new BusinessException(CustomerErrorCode.INVALID_CUSTOMER_DATA,
                    "Password must be at least 8 characters");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException(CustomerErrorCode.INVALID_CUSTOMER_DATA,
                    "Name must not be blank");
        }

        Customer customer = new Customer();
        customer.email = email;
        customer.passwordHash = BCrypt.hashpw(rawPassword, BCrypt.gensalt());
        customer.name = name;
        customer.phone = phone;
        return customer;
    }

    public boolean checkPassword(String raw) {
        return BCrypt.checkpw(raw, this.passwordHash);
    }

    public void updateProfile(String name, String phone) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (phone != null) {
            this.phone = phone;
        }
    }

    public void changePassword(String newRaw) {
        if (newRaw == null || newRaw.length() < 8) {
            throw new BusinessException(CustomerErrorCode.INVALID_CUSTOMER_DATA,
                    "Password must be at least 8 characters");
        }
        this.passwordHash = BCrypt.hashpw(newRaw, BCrypt.gensalt());
    }
}
