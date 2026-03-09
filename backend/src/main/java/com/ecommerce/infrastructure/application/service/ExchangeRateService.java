package com.ecommerce.infrastructure.application.service;

import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.infrastructure.domain.model.ExchangeRate;
import com.ecommerce.infrastructure.domain.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;

    @Transactional(readOnly = true)
    public ExchangeRate getLatestRate(String fromCurrency, String toCurrency) {
        Optional<ExchangeRate> direct = exchangeRateRepository
                .findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc(fromCurrency, toCurrency);
        if (direct.isPresent()) {
            return direct.get();
        }

        return exchangeRateRepository
                .findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc(toCurrency, fromCurrency)
                .orElseThrow(() -> new EntityNotFoundException(
                        "ExchangeRate for " + fromCurrency + " -> " + toCurrency));
    }

    @Transactional(readOnly = true)
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return amount.setScale(4, RoundingMode.HALF_UP);
        }

        Optional<ExchangeRate> direct = exchangeRateRepository
                .findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc(fromCurrency, toCurrency);

        if (direct.isPresent()) {
            return amount.multiply(direct.get().getRate()).setScale(4, RoundingMode.HALF_UP);
        }

        Optional<ExchangeRate> reverse = exchangeRateRepository
                .findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc(toCurrency, fromCurrency);

        if (reverse.isPresent()) {
            BigDecimal inverseRate = BigDecimal.ONE.divide(reverse.get().getRate(), 8, RoundingMode.HALF_UP);
            return amount.multiply(inverseRate).setScale(4, RoundingMode.HALF_UP);
        }

        throw new EntityNotFoundException("ExchangeRate for " + fromCurrency + " -> " + toCurrency);
    }

    @Transactional
    public ExchangeRate registerRate(String fromCurrency, String toCurrency, BigDecimal rate, LocalDate effectiveDate) {
        ExchangeRate exchangeRate = ExchangeRate.create(fromCurrency, toCurrency, rate, effectiveDate);
        return exchangeRateRepository.save(exchangeRate);
    }

    @Transactional(readOnly = true)
    public List<ExchangeRate> getHistory(String fromCurrency, String toCurrency) {
        return exchangeRateRepository.findByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc(fromCurrency, toCurrency);
    }
}
