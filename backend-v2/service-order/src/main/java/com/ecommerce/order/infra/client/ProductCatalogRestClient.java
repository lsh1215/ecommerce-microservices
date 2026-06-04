package com.ecommerce.order.infra.client;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import com.ecommerce.order.application.dto.ProductSnapshotDto;
import com.ecommerce.order.domain.service.ProductCatalogPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Product 서비스와 동기 HTTP로 통신하는 어댑터.
 *
 * <p>Product 의존성이 일시적으로 실패하면 Resilience4j Circuit Breaker가 빠르게 실패시켜
 * Order 서비스 요청 스레드 고갈을 막는다.
 *
 * <p>실패 분류:
 * <ul>
 *   <li>Product 5xx, 연결 실패, 타임아웃은 일시적 의존성 실패로 본다.</li>
 *   <li>Product 4xx 응답은 비즈니스 실패이므로 Circuit Breaker를 열지 않는다.</li>
 *   <li>열린 Circuit 호출은 {@link OrderErrorCode#PRODUCT_SERVICE_UNAVAILABLE}로 변환한다.</li>
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
        // retrieve()는 4xx도 예외로 던지므로, 검증성 404를 CB 실패로 기록하지 않기 위해 exchange()로 직접 분류한다.
        return restClient.get()
                .uri("/api/internal/products/variants/{variantId}", variantId)
                .exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();
                    if (status.is2xxSuccessful()) {
                        return true;
                    }
                    if (status.is4xxClientError()) {
                        return false;
                    }
                    throw new RestClientResponseException(
                            "Product service returned " + status.value(),
                            status.value(),
                            status.toString(),
                            HttpHeaders.EMPTY,
                            response.getBody().readAllBytes(),
                            StandardCharsets.UTF_8
                    );
                });
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
    public void reserveStock(Long orderId, Long variantId, int quantity) {
        restClient.post()
                .uri("/api/internal/products/variants/{variantId}/reserve-stock", variantId)
                .body(Map.of("orderId", orderId, "quantity", quantity))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    // 재고 부족 같은 4xx는 Product 장애가 아니라 주문 비즈니스 실패로 처리한다.
                    throw new BusinessException(OrderErrorCode.STOCK_RESERVATION_FAILED,
                            "Stock reservation failed for variant: " + variantId);
                })
                .toBodilessEntity();
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "releaseStockFallback")
    public void releaseStock(Long orderId, Long variantId, int quantity) {
        restClient.post()
                .uri("/api/internal/products/variants/{variantId}/release-stock", variantId)
                .body(Map.of("orderId", orderId, "quantity", quantity))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "confirmReservationFallback")
    public void confirmReservation(Long orderId, Long variantId) {
        restClient.post()
                .uri("/api/internal/products/variants/{variantId}/confirm-reservation", variantId)
                .body(Map.of("orderId", orderId))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * existsVariant는 읽기 검증 전용이므로 fallback에서 false를 반환한다.
     */
    @SuppressWarnings("unused")
    private boolean existsVariantFallback(Long variantId, Throwable t) {
        if (t instanceof BusinessException be) throw be;
        log.warn("[CB] existsVariant fallback for variantId={}: {}", variantId, t.toString());
        return false;
    }

    /**
     * fetchSnapshot fallback은 의존성 실패를 서비스 사용 불가 오류로 변환한다.
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
     * reserveStock fallback은 비즈니스 예외를 보존하고 의존성 실패만 감싼다.
     */
    @SuppressWarnings("unused")
    private void reserveStockFallback(Long orderId, Long variantId, int quantity, Throwable t) {
        if (t instanceof BusinessException be) throw be;
        log.warn("[CB] reserveStock fallback for orderId={}, variantId={}, qty={}: {}",
                orderId, variantId, quantity, t.toString());
        if (t instanceof CallNotPermittedException) {
            throw new BusinessException(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                    "Stock reservation unavailable (circuit open)");
        }
        throw new BusinessException(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                "Stock reservation temporarily unavailable");
    }

    /**
     * releaseStock fallback은 Saga가 재시도 필요 상태를 기록할 수 있도록 예외를 전파한다.
     */
    @SuppressWarnings("unused")
    private void releaseStockFallback(Long orderId, Long variantId, int quantity, Throwable t) {
        if (t instanceof BusinessException be) throw be;
        log.warn("[CB] releaseStock fallback for orderId={}, variantId={}, qty={}: {}",
                orderId, variantId, quantity, t.toString());
        // 보상 실패를 정상 반환하면 Saga가 재시도 필요 상태를 남길 수 없으므로 예외로 전파한다.
        if (t instanceof CallNotPermittedException) {
            throw new BusinessException(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                    "Stock release unavailable (circuit open)");
        }
        throw new BusinessException(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                "Stock release temporarily unavailable");
    }

    @SuppressWarnings("unused")
    private void confirmReservationFallback(Long orderId, Long variantId, Throwable t) {
        if (t instanceof BusinessException be) throw be;
        log.warn("[CB] confirmReservation fallback for orderId={}, variantId={}: {}",
                orderId, variantId, t.toString());
        if (t instanceof CallNotPermittedException) {
            throw new BusinessException(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                    "Stock confirmation unavailable (circuit open)");
        }
        throw new BusinessException(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                "Stock confirmation temporarily unavailable");
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
