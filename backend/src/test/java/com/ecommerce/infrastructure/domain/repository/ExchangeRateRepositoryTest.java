package com.ecommerce.infrastructure.domain.repository;

import com.ecommerce.common.config.JpaAuditingConfig;
import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.infrastructure.domain.model.ExchangeRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({TestContainersConfig.class, JpaAuditingConfig.class})
class ExchangeRateRepositoryTest {

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainersConfig.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainersConfig.MYSQL::getUsername);
        registry.add("spring.datasource.password", TestContainersConfig.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @BeforeEach
    void setUp() {
        exchangeRateRepository.deleteAll();
    }

    @Test
    void save_shouldPersistExchangeRateWithCreatedAt() {
        ExchangeRate rate = ExchangeRate.create("USD", "KRW", new BigDecimal("1300.00000000"), LocalDate.of(2024, 1, 1));

        ExchangeRate saved = exchangeRateRepository.save(rate);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getFromCurrency()).isEqualTo("USD");
        assertThat(saved.getToCurrency()).isEqualTo("KRW");
        assertThat(saved.getRate()).isEqualByComparingTo(new BigDecimal("1300.00000000"));
        assertThat(saved.getEffectiveDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc_shouldReturnLatest() {
        exchangeRateRepository.save(ExchangeRate.create("USD", "KRW", new BigDecimal("1300.00000000"), LocalDate.of(2024, 1, 1)));
        exchangeRateRepository.save(ExchangeRate.create("USD", "KRW", new BigDecimal("1350.00000000"), LocalDate.of(2024, 6, 1)));
        exchangeRateRepository.save(ExchangeRate.create("USD", "KRW", new BigDecimal("1280.00000000"), LocalDate.of(2023, 12, 1)));

        Optional<ExchangeRate> latest = exchangeRateRepository
                .findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc("USD", "KRW");

        assertThat(latest).isPresent();
        assertThat(latest.get().getRate()).isEqualByComparingTo(new BigDecimal("1350.00000000"));
        assertThat(latest.get().getEffectiveDate()).isEqualTo(LocalDate.of(2024, 6, 1));
    }

    @Test
    void findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc_shouldReturnEmptyWhenNone() {
        Optional<ExchangeRate> result = exchangeRateRepository
                .findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc("USD", "JPY");

        assertThat(result).isEmpty();
    }

    @Test
    void findByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc_shouldReturnAllInOrder() {
        exchangeRateRepository.save(ExchangeRate.create("USD", "KRW", new BigDecimal("1300.00000000"), LocalDate.of(2024, 1, 1)));
        exchangeRateRepository.save(ExchangeRate.create("USD", "KRW", new BigDecimal("1350.00000000"), LocalDate.of(2024, 6, 1)));
        exchangeRateRepository.save(ExchangeRate.create("USD", "KRW", new BigDecimal("1280.00000000"), LocalDate.of(2023, 12, 1)));

        List<ExchangeRate> history = exchangeRateRepository
                .findByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc("USD", "KRW");

        assertThat(history).hasSize(3);
        assertThat(history.get(0).getEffectiveDate()).isEqualTo(LocalDate.of(2024, 6, 1));
        assertThat(history.get(1).getEffectiveDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(history.get(2).getEffectiveDate()).isEqualTo(LocalDate.of(2023, 12, 1));
    }

    @Test
    void save_shouldNotHaveUpdatedAt() {
        ExchangeRate rate = ExchangeRate.create("EUR", "USD", new BigDecimal("1.08000000"), LocalDate.of(2024, 1, 1));

        ExchangeRate saved = exchangeRateRepository.save(rate);

        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
