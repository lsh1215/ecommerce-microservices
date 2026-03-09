package com.ecommerce.infrastructure.api.dto.response;

import com.ecommerce.infrastructure.domain.model.ExchangeRate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExchangeRateResponse(
        Long id,
        String fromCurrency,
        String toCurrency,
        BigDecimal rate,
        LocalDate effectiveDate,
        LocalDateTime createdAt
) {
    public static ExchangeRateResponse from(ExchangeRate exchangeRate) {
        return new ExchangeRateResponse(
                exchangeRate.getId(),
                exchangeRate.getFromCurrency(),
                exchangeRate.getToCurrency(),
                exchangeRate.getRate(),
                exchangeRate.getEffectiveDate(),
                exchangeRate.getCreatedAt()
        );
    }
}
