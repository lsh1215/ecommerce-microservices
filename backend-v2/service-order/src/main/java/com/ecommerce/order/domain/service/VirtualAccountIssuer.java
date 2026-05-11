package com.ecommerce.order.domain.service;

import com.ecommerce.order.domain.model.VirtualAccountInstruction;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class VirtualAccountIssuer {

    private static final List<String> BANKS = List.of("KB", "SHINHAN", "WOORI", "HANA", "NH");
    private static final String HOLDER_NAME = "ECOMMERCE STORE";
    private static final BigInteger ACCOUNT_MOD = BigInteger.TEN.pow(14);
    private static final int ACCOUNT_DIGITS = 14;

    public VirtualAccountInstruction issue(String orderNumber, BigDecimal amount, LocalDateTime expiresAt) {
        byte[] digest = sha256(orderNumber);

        byte[] accountBytes = new byte[8];
        System.arraycopy(digest, 0, accountBytes, 0, 8);
        BigInteger accountSeed = new BigInteger(1, accountBytes);
        String accountNumber = String.format("%0" + ACCOUNT_DIGITS + "d",
                accountSeed.mod(ACCOUNT_MOD));

        byte[] bankBytes = new byte[8];
        System.arraycopy(digest, 8, bankBytes, 0, 8);
        BigInteger bankSeed = new BigInteger(1, bankBytes);
        String bank = BANKS.get(bankSeed.mod(BigInteger.valueOf(BANKS.size())).intValue());

        return new VirtualAccountInstruction(bank, accountNumber, HOLDER_NAME, amount, expiresAt);
    }

    private byte[] sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
