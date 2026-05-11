package com.ecommerce.order.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class VirtualAccountInstruction {

    @Column(name = "va_bank", nullable = false)
    private String bank;

    @Column(name = "va_account_number", nullable = false)
    private String accountNumber;

    @Column(name = "va_holder_name", nullable = false)
    private String holderName;

    @Column(name = "va_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "va_expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public VirtualAccountInstruction(String bank, String accountNumber, String holderName,
                                     BigDecimal amount, LocalDateTime expiresAt) {
        this.bank = Objects.requireNonNull(bank, "bank");
        this.accountNumber = Objects.requireNonNull(accountNumber, "accountNumber");
        this.holderName = Objects.requireNonNull(holderName, "holderName");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VirtualAccountInstruction that)) return false;
        return Objects.equals(bank, that.bank)
                && Objects.equals(accountNumber, that.accountNumber)
                && Objects.equals(holderName, that.holderName)
                && Objects.equals(amount, that.amount)
                && Objects.equals(expiresAt, that.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bank, accountNumber, holderName, amount, expiresAt);
    }
}
