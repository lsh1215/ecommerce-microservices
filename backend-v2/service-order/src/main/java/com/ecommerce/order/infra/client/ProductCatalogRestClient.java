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

/**
 * No-CB variant: synchronous RestClient adapter without Resilience4j.
 *
 * <p>Used by the no-cb evidence worktree to demonstrate the failure mode that
 * Phase 4's circuit breaker prevents — Tomcat thread-pool exhaustion when
 * Product is slow.
 */
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
                        // 404 swallowed — read-only existence check returns false.
                    })
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    @Override
    public ProductSnapshotDto fetchSnapshot(Long variantId) {
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
    }

    @Override
    public void reserveStock(Long variantId, int quantity) {
        restClient.post()
                .uri("/api/internal/products/variants/{variantId}/reserve-stock", variantId)
                .body(Map.of("quantity", quantity))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new BusinessException(OrderErrorCode.STOCK_RESERVATION_FAILED,
                            "Stock reservation failed for variant: " + variantId);
                })
                .toBodilessEntity();
    }

    @Override
    public void releaseStock(Long variantId, int quantity) {
        restClient.post()
                .uri("/api/internal/products/variants/{variantId}/release-stock", variantId)
                .body(Map.of("quantity", quantity))
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(Map<String, Object> body) {
        if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
            throw new BusinessException(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                    "Product service returned malformed response");
        }
        Object data = body.get("data");
        if (!(data instanceof Map)) {
            throw new BusinessException(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                    "Product service returned malformed data");
        }
        return (Map<String, Object>) data;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }
}
