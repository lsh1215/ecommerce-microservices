package com.ecommerce.infrastructure.application.service;

import com.ecommerce.common.exception.EntityNotFoundException;
import com.ecommerce.infrastructure.domain.model.ExchangeRate;
import com.ecommerce.infrastructure.domain.repository.ExchangeRateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @InjectMocks
    private ExchangeRateService exchangeRateService;

    @Test
    void getLatestRate_shouldReturnRateFromRepository() {
        ExchangeRate rate = ExchangeRate.create("USD", "KRW", new BigDecimal("1300.00000000"), LocalDate.of(2024, 1, 1));
        given(exchangeRateRepository.findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc("USD", "KRW"))
                .willReturn(Optional.of(rate));

        ExchangeRate result = exchangeRateService.getLatestRate("USD", "KRW");

        assertThat(result.getFromCurrency()).isEqualTo("USD");
        assertThat(result.getToCurrency()).isEqualTo("KRW");
        assertThat(result.getRate()).isEqualByComparingTo(new BigDecimal("1300.00000000"));
    }

    @Test
    void getLatestRate_shouldThrowEntityNotFoundExceptionWhenNoRateInEitherDirection() {
        given(exchangeRateRepository.findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc("USD", "JPY"))
                .willReturn(Optional.empty());
        given(exchangeRateRepository.findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc("JPY", "USD"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> exchangeRateService.getLatestRate("USD", "JPY"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getLatestRate_shouldReturnReverseRecordWhenDirectNotFound() {
        ExchangeRate usdToKrw = ExchangeRate.create("USD", "KRW", new BigDecimal("1300.00000000"), LocalDate.of(2024, 1, 1));
        given(exchangeRateRepository.findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc("KRW", "USD"))
                .willReturn(Optional.empty());
        given(exchangeRateRepository.findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc("USD", "KRW"))
                .willReturn(Optional.of(usdToKrw));

        ExchangeRate result = exchangeRateService.getLatestRate("KRW", "USD");

        assertThat(result.getFromCurrency()).isEqualTo("USD");
        assertThat(result.getToCurrency()).isEqualTo("KRW");
    }

    @Test
    void convert_sameCurrency_shouldReturnSameAmountWithoutDbLookup() {
        BigDecimal amount = new BigDecimal("100.00");

        BigDecimal result = exchangeRateService.convert(amount, "USD", "USD");

        assertThat(result).isEqualByComparingTo(new BigDecimal("100.0000"));
        verify(exchangeRateRepository, never()).findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc(any(), any());
    }

    @Test
    void convert_shouldMultiplyAmountByRate() {
        ExchangeRate rate = ExchangeRate.create("USD", "KRW", new BigDecimal("1300.00000000"), LocalDate.of(2024, 1, 1));
        given(exchangeRateRepository.findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc("USD", "KRW"))
                .willReturn(Optional.of(rate));

        BigDecimal result = exchangeRateService.convert(new BigDecimal("10"), "USD", "KRW");

        assertThat(result).isEqualByComparingTo(new BigDecimal("13000.0000"));
    }

    @Test
    void convert_shouldRoundToFourDecimalPlacesHalfUp() {
        ExchangeRate rate = ExchangeRate.create("USD", "KRW", new BigDecimal("3.33333333"), LocalDate.of(2024, 1, 1));
        given(exchangeRateRepository.findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc("USD", "KRW"))
                .willReturn(Optional.of(rate));

        BigDecimal result = exchangeRateService.convert(new BigDecimal("1"), "USD", "KRW");

        BigDecimal expected = new BigDecimal("3.33333333").multiply(BigDecimal.ONE)
                .setScale(4, RoundingMode.HALF_UP);
        assertThat(result).isEqualByComparingTo(expected);
    }

    @Test
    void convert_reverseRate_shouldUseInverseWhenDirectRateNotFound() {
        ExchangeRate usdToKrw = ExchangeRate.create("USD", "KRW", new BigDecimal("1300.00000000"), LocalDate.of(2024, 1, 1));
        given(exchangeRateRepository.findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc("KRW", "USD"))
                .willReturn(Optional.empty());
        given(exchangeRateRepository.findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc("USD", "KRW"))
                .willReturn(Optional.of(usdToKrw));

        BigDecimal result = exchangeRateService.convert(new BigDecimal("1300"), "KRW", "USD");

        BigDecimal expectedRate = BigDecimal.ONE.divide(new BigDecimal("1300.00000000"), 8, RoundingMode.HALF_UP);
        BigDecimal expected = new BigDecimal("1300").multiply(expectedRate).setScale(4, RoundingMode.HALF_UP);
        assertThat(result).isEqualByComparingTo(expected);
    }

    @Test
    void convert_shouldThrowEntityNotFoundWhenNoRateInEitherDirection() {
        given(exchangeRateRepository.findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc("KRW", "JPY"))
                .willReturn(Optional.empty());
        given(exchangeRateRepository.findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc("JPY", "KRW"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> exchangeRateService.convert(new BigDecimal("1000"), "KRW", "JPY"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void registerRate_shouldSaveAndReturnExchangeRate() {
        ExchangeRate rate = ExchangeRate.create("EUR", "USD", new BigDecimal("1.08000000"), LocalDate.of(2024, 1, 1));
        given(exchangeRateRepository.save(any(ExchangeRate.class))).willReturn(rate);

        ExchangeRate result = exchangeRateService.registerRate("EUR", "USD", new BigDecimal("1.08000000"), LocalDate.of(2024, 1, 1));

        assertThat(result.getFromCurrency()).isEqualTo("EUR");
        assertThat(result.getToCurrency()).isEqualTo("USD");
        verify(exchangeRateRepository).save(any(ExchangeRate.class));
    }
}
