package com.ecommerce.infrastructure.domain.repository;

import com.ecommerce.infrastructure.domain.model.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc(
            String fromCurrency, String toCurrency);

    List<ExchangeRate> findByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc(
            String fromCurrency, String toCurrency);
}
