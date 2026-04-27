package com.ecommerce.order.infra.client;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * No-saga variant: synchronous Payment caller.
 *
 * <p>Hits {@code POST /api/payments/process} on service-payment and waits for
 * the response before returning. Order POST blocks for the full duration of
 * payment processing — exposes the latency-coupling failure mode that
 * Phase 1's SAGA + Kafka decoupling avoids.
 */
@Component
public class PaymentSyncClient {

    private final RestClient restClient;

    public PaymentSyncClient(@Qualifier("paymentRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public Result process(Long orderId, String orderNumber, BigDecimal amount) {
        try {
            Map<String, Object> body = restClient.post()
                    .uri("/api/payments/process")
                    .body(Map.of(
                            "orderId", orderId,
                            "orderNumber", orderNumber,
                            "amount", amount,
                            "paymentMethod", "CARD"
                    ))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new BusinessException(OrderErrorCode.PAYMENT_FAILED,
                                "Payment rejected by service-payment");
                    })
                    .body(new ParameterizedTypeReference<>() {});

            if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
                throw new BusinessException(OrderErrorCode.PAYMENT_FAILED,
                        "Payment service returned malformed response");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            String status = String.valueOf(data.get("status"));
            Long paymentId = ((Number) data.get("id")).longValue();
            Object txn = data.get("transactionId");
            return new Result(
                    "COMPLETED".equals(status),
                    paymentId,
                    txn != null ? txn.toString() : null);
        } catch (RestClientException e) {
            // 5xx, connection failure, timeout — synchronous failure leaks into the order POST.
            throw new BusinessException(OrderErrorCode.PAYMENT_FAILED,
                    "Payment service unavailable: " + e.getMessage());
        }
    }

    public record Result(boolean success, Long paymentId, String transactionId) {}
}
