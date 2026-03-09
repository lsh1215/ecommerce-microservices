package com.ecommerce.infrastructure.api.controller;

import com.ecommerce.common.config.TestContainersConfig;
import com.ecommerce.infrastructure.domain.model.ExchangeRate;
import com.ecommerce.infrastructure.domain.repository.ExchangeRateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class ExchangeRateControllerTest {

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainersConfig.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainersConfig.MYSQL::getUsername);
        registry.add("spring.datasource.password", TestContainersConfig.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @BeforeEach
    void setUp() {
        exchangeRateRepository.deleteAll();
    }

    @Test
    void getLatestRate_shouldReturn200WithLatestRate() throws Exception {
        exchangeRateRepository.save(ExchangeRate.create("USD", "KRW", new BigDecimal("1300.00000000"), LocalDate.of(2024, 1, 1)));
        exchangeRateRepository.save(ExchangeRate.create("USD", "KRW", new BigDecimal("1350.00000000"), LocalDate.of(2024, 6, 1)));

        mockMvc.perform(get("/api/exchange-rates")
                        .param("from", "USD")
                        .param("to", "KRW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.fromCurrency", is("USD")))
                .andExpect(jsonPath("$.data.toCurrency", is("KRW")))
                .andExpect(jsonPath("$.data.rate", is(1350.00000000)))
                .andExpect(jsonPath("$.data.effectiveDate", is("2024-06-01")))
                .andExpect(jsonPath("$.data.createdAt", notNullValue()));
    }

    @Test
    void getLatestRate_shouldReturn404WhenNoRate() throws Exception {
        mockMvc.perform(get("/api/exchange-rates")
                        .param("from", "USD")
                        .param("to", "JPY"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void registerRate_shouldReturn201WithCreatedRate() throws Exception {
        Map<String, Object> request = Map.of(
                "fromCurrency", "EUR",
                "toCurrency", "USD",
                "rate", "1.08000000",
                "effectiveDate", "2024-01-01"
        );

        mockMvc.perform(post("/api/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.fromCurrency", is("EUR")))
                .andExpect(jsonPath("$.data.toCurrency", is("USD")))
                .andExpect(jsonPath("$.data.effectiveDate", is("2024-01-01")));
    }

    @Test
    void registerRate_shouldReturn400WhenInvalidRequest() throws Exception {
        Map<String, Object> request = Map.of(
                "fromCurrency", "US",
                "toCurrency", "KRW",
                "rate", "1300.00",
                "effectiveDate", "2024-01-01"
        );

        mockMvc.perform(post("/api/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void getHistory_shouldReturn200WithAllRatesInDescOrder() throws Exception {
        exchangeRateRepository.save(ExchangeRate.create("USD", "KRW", new BigDecimal("1280.00000000"), LocalDate.of(2023, 12, 1)));
        exchangeRateRepository.save(ExchangeRate.create("USD", "KRW", new BigDecimal("1300.00000000"), LocalDate.of(2024, 1, 1)));
        exchangeRateRepository.save(ExchangeRate.create("USD", "KRW", new BigDecimal("1350.00000000"), LocalDate.of(2024, 6, 1)));

        mockMvc.perform(get("/api/exchange-rates/history")
                        .param("from", "USD")
                        .param("to", "KRW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].effectiveDate", is("2024-06-01")))
                .andExpect(jsonPath("$.data[1].effectiveDate", is("2024-01-01")))
                .andExpect(jsonPath("$.data[2].effectiveDate", is("2023-12-01")));
    }

    @Test
    void getHistory_shouldReturn200WithEmptyListWhenNoHistory() throws Exception {
        mockMvc.perform(get("/api/exchange-rates/history")
                        .param("from", "USD")
                        .param("to", "JPY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void getLatestRate_reverseRate_shouldReturnInverseWhenOnlyDirectExists() throws Exception {
        exchangeRateRepository.save(ExchangeRate.create("USD", "KRW", new BigDecimal("1300.00000000"), LocalDate.of(2024, 1, 1)));

        mockMvc.perform(get("/api/exchange-rates")
                        .param("from", "KRW")
                        .param("to", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.fromCurrency", is("USD")))
                .andExpect(jsonPath("$.data.toCurrency", is("KRW")));
    }
}
