package com.ecommerce.order.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.order.domain.model.VirtualAccountInstruction;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class VirtualAccountIssuerTest {

    private static final List<String> EXPECTED_BANKS = List.of("KB", "SHINHAN", "WOORI", "HANA", "NH");

    private final VirtualAccountIssuer issuer = new VirtualAccountIssuer();

    @Test
    void issue_isDeterministicForSameOrderNumber() {
        BigDecimal amount = new BigDecimal("12345.00");
        LocalDateTime expiresAt = LocalDateTime.of(2026, 5, 13, 12, 0);

        VirtualAccountInstruction first = issuer.issue("ORDER-001", amount, expiresAt);
        VirtualAccountInstruction second = issuer.issue("ORDER-001", amount, expiresAt);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void issue_producesDifferentAccountForDifferentOrderNumber() {
        BigDecimal amount = new BigDecimal("100.00");
        LocalDateTime expiresAt = LocalDateTime.of(2026, 5, 13, 12, 0);

        VirtualAccountInstruction a = issuer.issue("ORDER-A", amount, expiresAt);
        VirtualAccountInstruction b = issuer.issue("ORDER-B", amount, expiresAt);

        assertThat(a.getAccountNumber()).isNotEqualTo(b.getAccountNumber());
    }

    @Test
    void issue_accountNumberIs14NumericDigits() {
        VirtualAccountInstruction instruction = issuer.issue("ORDER-001",
                new BigDecimal("100.00"), LocalDateTime.of(2026, 5, 13, 12, 0));

        assertThat(instruction.getAccountNumber()).hasSize(14);
        assertThat(instruction.getAccountNumber()).matches("\\d{14}");
    }

    @Test
    void issue_bankIsFromAllowedList() {
        VirtualAccountInstruction instruction = issuer.issue("ORDER-001",
                new BigDecimal("100.00"), LocalDateTime.of(2026, 5, 13, 12, 0));

        assertThat(EXPECTED_BANKS).contains(instruction.getBank());
    }

    @Test
    void issue_holderIsFixed() {
        VirtualAccountInstruction instruction = issuer.issue("ORDER-001",
                new BigDecimal("100.00"), LocalDateTime.of(2026, 5, 13, 12, 0));

        assertThat(instruction.getHolderName()).isEqualTo("ECOMMERCE STORE");
    }

    @Test
    void issue_mirrorsAmountAndExpiresAt() {
        BigDecimal amount = new BigDecimal("99.99");
        LocalDateTime expiresAt = LocalDateTime.of(2026, 5, 13, 12, 0);

        VirtualAccountInstruction instruction = issuer.issue("ORDER-001", amount, expiresAt);

        assertThat(instruction.getAmount()).isEqualByComparingTo(amount);
        assertThat(instruction.getExpiresAt()).isEqualTo(expiresAt);
    }
}
