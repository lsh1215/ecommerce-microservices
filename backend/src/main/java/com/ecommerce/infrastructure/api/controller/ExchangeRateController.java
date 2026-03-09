package com.ecommerce.infrastructure.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.infrastructure.api.dto.request.CreateExchangeRateRequest;
import com.ecommerce.infrastructure.api.dto.response.ExchangeRateResponse;
import com.ecommerce.infrastructure.application.service.ExchangeRateService;
import com.ecommerce.infrastructure.domain.model.ExchangeRate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping
    public ResponseEntity<ApiResponse<ExchangeRateResponse>> getLatestRate(
            @RequestParam String from,
            @RequestParam String to) {
        ExchangeRate rate = exchangeRateService.getLatestRate(from, to);
        return ResponseEntity.ok(ApiResponse.success(ExchangeRateResponse.from(rate)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExchangeRateResponse>> registerRate(
            @Valid @RequestBody CreateExchangeRateRequest request) {
        ExchangeRate rate = exchangeRateService.registerRate(
                request.fromCurrency(),
                request.toCurrency(),
                request.rate(),
                request.effectiveDate());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ExchangeRateResponse.from(rate)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ExchangeRateResponse>>> getHistory(
            @RequestParam String from,
            @RequestParam String to) {
        List<ExchangeRateResponse> history = exchangeRateService.getHistory(from, to)
                .stream()
                .map(ExchangeRateResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(history));
    }
}
