package com.ecommerce.order.infra.client;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import com.ecommerce.order.application.dto.ProductSnapshotDto;
import com.ecommerce.order.domain.service.ProductCatalogPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class ProductCatalogRestClient implements ProductCatalogPort {

    private final RestClient restClient;

    public ProductCatalogRestClient(@Qualifier("productRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public boolean existsVariant(Long variantId) {
        try {
            restClient.get()
                    .uri("/api/internal/products/variants/{variantId}", variantId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        // 404: Variant가 존재하지 않음
                    })
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    @Override
    public ProductSnapshotDto fetchSnapshot(Long variantId) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/api/internal/products/variants/{variantId}", variantId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new BusinessException(OrderErrorCode.STOCK_RESERVATION_FAILED,
                                "Product variant not found: " + variantId);
                    })
                    .body(new ParameterizedTypeReference<>() {});

            Map<String, Object> data = extractData(body);
            return new ProductSnapshotDto(
                    toLong(data.get("productId")),
                    toLong(data.get("variantId")),
                    (String) data.get("productName"),
                    (String) data.get("size"),
                    (String) data.get("color"),
                    new java.math.BigDecimal(data.get("unitPrice").toString())
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                    "Failed to fetch product snapshot: " + e.getMessage());
        }
    }

    @Override
    public void reserveStock(Long variantId, int quantity) {
        try {
            restClient.post()
                    .uri("/api/internal/products/variants/{variantId}/reserve-stock", variantId)
                    .body(Map.of("quantity", quantity))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new BusinessException(OrderErrorCode.STOCK_RESERVATION_FAILED,
                                "Stock reservation failed for variant: " + variantId);
                    })
                    .toBodilessEntity();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                    "Failed to reserve stock: " + e.getMessage());
        }
    }

    @Override
    public void releaseStock(Long variantId, int quantity) {
        try {
            restClient.post()
                    .uri("/api/internal/products/variants/{variantId}/release-stock", variantId)
                    .body(Map.of("quantity", quantity))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new BusinessException(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                    "Failed to release stock: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(Map<String, Object> response) {
        if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
            throw new BusinessException(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                    "Unexpected response from product service");
        }
        return (Map<String, Object>) response.get("data");
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
