package com.ecommerce.order.infra.client;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import com.ecommerce.order.application.dto.ProductSnapshotDto;
import com.ecommerce.order.domain.service.ProductCatalogPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Synchronous HTTP adapter for the Product service.
 *
 * <p>The Resilience4j circuit breaker fast-fails transient Product dependency failures
 * so Order service request threads are not exhausted.
 *
 * <p>Failure classification:
 * <ul>
 *   <li>Product 5xx, connection failures, and timeouts are transient dependency failures.</li>
 *   <li>Product 4xx responses are business failures and must not open the circuit.</li>
 *   <li>Open-circuit calls are wrapped as {@link OrderErrorCode#PRODUCT_SERVICE_UNAVAILABLE}.</li>
 * </ul>
 */
@Component
public class ProductCatalogRestClient implements ProductCatalogPort {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogRestClient.class);
    private static final String CB_NAME = "productService";

    private final RestClient restClient;

    public ProductCatalogRestClient(@Qualifier("productRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "existsVariantFallback")
    public boolean existsVariant(Long variantId) {
        try {
            restClient.get()
                    .uri("/api/internal/products/variants/{variantId}", variantId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {})
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            throw e;
        }
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fetchSnapshotFallback")
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
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "reserveStockFallback")
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
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "releaseStockFallback")
    public void releaseStock(Long variantId, int quantity) {
        restClient.post()
                .uri("/api/internal/products/variants/{variantId}/release-stock", variantId)
                .body(Map.of("quantity", quantity))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * existsVariant fallback returns false because this method is only a read-side validation.
     */
    @SuppressWarnings("unused")
    private boolean existsVariantFallback(Long variantId, Throwable t) {
        if (t instanceof BusinessException be) throw be;
        log.warn("[CB] existsVariant fallback for variantId={}: {}", variantId, t.toString());
        return false;
    }

    /**
     * fetchSnapshot fallback converts dependency failures to service-unavailable errors.
     */
    @SuppressWarnings("unused")
    private ProductSnapshotDto fetchSnapshotFallback(Long variantId, Throwable t) {
        if (t instanceof BusinessException be) throw be;
        log.warn("[CB] fetchSnapshot fallback for variantId={}: {}", variantId, t.toString());
        if (t instanceof CallNotPermittedException) {
            throw new BusinessException(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                    "Product service is currently unavailable (circuit open)");
        }
        throw new BusinessException(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                "Product service is currently unavailable");
    }

    /**
     * reserveStock fallback preserves business exceptions and wraps dependency failures.
     */
    @SuppressWarnings("unused")
    private void reserveStockFallback(Long variantId, int quantity, Throwable t) {
        if (t instanceof BusinessException be) throw be;
        log.warn("[CB] reserveStock fallback for variantId={}, qty={}: {}", variantId, quantity, t.toString());
        if (t instanceof CallNotPermittedException) {
            throw new BusinessException(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                    "Stock reservation unavailable (circuit open)");
        }
        throw new BusinessException(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                "Stock reservation temporarily unavailable");
    }

    /**
     * releaseStock fallback currently logs the failed compensation attempt.
     */
    @SuppressWarnings("unused")
    private void releaseStockFallback(Long variantId, int quantity, Throwable t) {
        log.warn("[CB] releaseStock fallback — will need retry: variantId={}, qty={}, cause={}",
                variantId, quantity, t.toString());
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
