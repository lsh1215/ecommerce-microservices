package com.ecommerce.order.infra.client;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import com.ecommerce.order.application.dto.PaymentResult;
import com.ecommerce.order.domain.service.PaymentRequestPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class PaymentRequestClient implements PaymentRequestPort {

    private final RestClient restClient;

    public PaymentRequestClient(@Qualifier("paymentRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public PaymentResult requestPayment(Long orderId, String orderNumber, BigDecimal amount) {
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
                                "Payment processing failed for order: " + orderNumber);
                    })
                    .body(new ParameterizedTypeReference<>() {});

            Map<String, Object> data = extractData(body);
            return new PaymentResult(
                    toLong(data.get("id")),
                    !"FAILED".equals(data.get("status")),
                    (String) data.get("transactionId"),
                    (String) data.get("failureReason")
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(OrderErrorCode.PAYMENT_FAILED,
                    "Payment service unavailable: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(Map<String, Object> response) {
        if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
            throw new BusinessException(OrderErrorCode.PAYMENT_FAILED,
                    "Unexpected response from payment service");
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
