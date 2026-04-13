package com.ecommerce.order.infra.client;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import com.ecommerce.order.domain.service.CustomerDirectoryPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class CustomerDirectoryRestClient implements CustomerDirectoryPort {

    private final RestClient restClient;

    public CustomerDirectoryRestClient(@Qualifier("customerRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public boolean existsCustomer(Long customerId) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/api/internal/customers/{id}/exists", customerId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new BusinessException(OrderErrorCode.CUSTOMER_NOT_FOUND,
                                "Customer not found: " + customerId);
                    })
                    .body(new ParameterizedTypeReference<>() {});

            if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
                return false;
            }
            Object data = body.get("data");
            return Boolean.TRUE.equals(data);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(OrderErrorCode.CUSTOMER_NOT_FOUND,
                    "Failed to verify customer: " + e.getMessage());
        }
    }
}
