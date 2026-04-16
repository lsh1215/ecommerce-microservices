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
 * Product 서비스와의 동기 HTTP 통신 어댑터.
 *
 * <p>Resilience4j Circuit Breaker가 적용되어 있어 Product 서비스가 느려지거나 실패하면
 * fast-fail하여 Order 서비스의 Tomcat 스레드 풀 고갈을 방지한다. (Phase 4)
 *
 * <p>설계 원칙:
 * <ul>
 *   <li>서비스 장애(5xx, 커넥션 실패, 타임아웃)는 원시 예외로 그대로 전파 — CB가 감지</li>
 *   <li>비즈니스 규칙 위반(4xx, 변형 미존재)은 BusinessException으로 감싸서 전파 — CB는 무시</li>
 *   <li>CB가 OPEN이거나 recordException이 잡히면 fallback 메서드에서 BusinessException으로 래핑</li>
 * </ul>
 *
 * <p>상태 전이: CLOSED → (실패율/slow call 임계 초과) → OPEN → (10초) → HALF_OPEN → 성공 시 CLOSED
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
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        // 404는 read-only 검증의 정상 케이스 — 예외 없이 false 반환하도록 swallow
                    })
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            // 4xx 이외의 예외는 CB에 노출되도록 재전파
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
                    // 4xx는 비즈니스 예외 — CB에 기록되지 않음
                    throw new BusinessException(OrderErrorCode.STOCK_RESERVATION_FAILED,
                            "Product variant not found: " + variantId);
                })
                .body(new ParameterizedTypeReference<>() {});
        // 5xx나 커넥션 실패는 Spring RestClient가 던지는 원시 예외로 CB에 전파됨

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
                    // 재고 부족 등 비즈니스 규칙 위반 — CB 실패 카운트 대상 아님
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

    // === Fallback methods (CB OPEN 또는 recorded exception 발생 시 호출) ===

    /**
     * existsVariant fallback — 검증 실패 시 false 반환.
     * BusinessException(4xx)은 fallback을 건너뛰고 그대로 전파됨.
     */
    @SuppressWarnings("unused")
    private boolean existsVariantFallback(Long variantId, Throwable t) {
        if (t instanceof BusinessException be) throw be;
        log.warn("[CB] existsVariant fallback for variantId={}: {}", variantId, t.toString());
        return false;
    }

    /**
     * fetchSnapshot fallback — 서비스 장애 시 503, 비즈니스 예외는 그대로 전파.
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
     * reserveStock fallback — 서비스 장애 시 503, 비즈니스 예외(재고 부족 등)는 그대로 전파.
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
     * releaseStock fallback — 보상 트랜잭션의 일부이므로 실패해도 진행 (log만 남김).
     * 추후 재시도 배치/이벤트 기반 보상으로 보완 가능.
     */
    @SuppressWarnings("unused")
    private void releaseStockFallback(Long variantId, int quantity, Throwable t) {
        log.warn("[CB] releaseStock fallback — will need retry: variantId={}, qty={}, cause={}",
                variantId, quantity, t.toString());
        // 보상 실패가 전체 취소 흐름을 막으면 안 됨 — swallow
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
