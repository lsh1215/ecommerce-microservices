package com.ecommerce.order.infra.client;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import com.ecommerce.order.domain.service.CustomerDirectoryPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Customer 서비스와의 동기 HTTP 통신 어댑터.
 *
 * <p>Resilience4j Circuit Breaker 적용 — Customer 서비스 장애 시 fast-fail. (Phase 4)
 */
@Component
public class CustomerDirectoryRestClient implements CustomerDirectoryPort {

    private static final Logger log = LoggerFactory.getLogger(CustomerDirectoryRestClient.class);
    private static final String CB_NAME = "customerService";

    private final RestClient restClient;

    public CustomerDirectoryRestClient(@Qualifier("customerRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "existsCustomerFallback")
    public boolean existsCustomer(Long customerId) {
        Map<String, Object> body = restClient.get()
                .uri("/api/internal/customers/{id}/exists", customerId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    // 404: 고객 미존재 — 비즈니스 예외, CB 미기록
                    throw new BusinessException(OrderErrorCode.CUSTOMER_NOT_FOUND,
                            "Customer not found: " + customerId);
                })
                .body(new ParameterizedTypeReference<>() {});
        // 5xx/커넥션 실패는 원시 예외로 전파되어 CB에 기록됨

        if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
            return false;
        }
        Object data = body.get("data");
        return Boolean.TRUE.equals(data);
    }

    /**
     * existsCustomer fallback — 서비스 장애 시 503 반환, 비즈니스 예외는 그대로 전파.
     */
    @SuppressWarnings("unused")
    private boolean existsCustomerFallback(Long customerId, Throwable t) {
        if (t instanceof BusinessException be) throw be;
        log.warn("[CB] existsCustomer fallback for customerId={}: {}", customerId, t.toString());
        if (t instanceof CallNotPermittedException) {
            throw new BusinessException(OrderErrorCode.CUSTOMER_SERVICE_UNAVAILABLE,
                    "Customer verification unavailable (circuit open)");
        }
        throw new BusinessException(OrderErrorCode.CUSTOMER_SERVICE_UNAVAILABLE,
                "Customer service is currently unavailable");
    }
}
