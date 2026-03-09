package com.ecommerce.infrastructure.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateExchangeRateRequest(
        @NotBlank @Size(min = 3, max = 3) String fromCurrency,
        @NotBlank @Size(min = 3, max = 3) String toCurrency,
        @NotNull @Positive BigDecimal rate,
        @NotNull LocalDate effectiveDate
) {}
