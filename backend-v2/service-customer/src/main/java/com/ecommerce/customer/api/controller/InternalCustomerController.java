package com.ecommerce.customer.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.customer.api.dto.response.CustomerResponse;
import com.ecommerce.customer.application.service.CustomerService;
import com.ecommerce.customer.domain.model.Customer;
import com.ecommerce.customer.domain.repository.CustomerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/customers")
@RequiredArgsConstructor
@Slf4j
public class InternalCustomerController {

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/{id}/exists")
    public ApiResponse<Boolean> exists(@PathVariable Long id) {
        boolean exists = customerRepository.existsById(id);
        return ApiResponse.ok(exists);
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> getCustomer(@PathVariable Long id) {
        Customer customer = customerService.getProfile(id);
        return ApiResponse.ok(CustomerResponse.from(customer));
    }

    /**
     * Traefik forwardAuth target.
     *
     * <p>Decodes the JWT payload from the {@code Authorization: Bearer <token>}
     * header and emits the {@code X-Customer-Id} header that downstream services
     * (Order in particular) trust without re-validating. Signature verification
     * is intentionally not performed in this demo build — the goal is the
     * pattern (auth at edge, services trust headers), not a production-grade
     * verifier. Replace this with a JJWT-backed verifier and rotated key
     * before any non-demo deployment.
     *
     * <p>Returns 200 with the {@code X-Customer-Id} header on success, 401 if
     * the bearer token is missing or malformed. Traefik blocks the upstream
     * request on any non-2xx response from this endpoint.
     */
    @GetMapping("/verify")
    public ResponseEntity<Void> verify(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Customer-Id", required = false) String trustedCustomerId) {
        // Demo escape-hatch: when the request already carries X-Customer-Id
        // (e.g. internal cluster calls or local dev), pass it through.
        if (trustedCustomerId != null && !trustedCustomerId.isBlank()) {
            return ResponseEntity.ok().header("X-Customer-Id", trustedCustomerId).build();
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("verify: missing bearer token");
            return ResponseEntity.status(401).build();
        }

        try {
            String token = authHeader.substring("Bearer ".length()).trim();
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return ResponseEntity.status(401).build();
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode payload = objectMapper.readTree(payloadJson);
            String sub = payload.path("sub").asText();
            if (sub.isEmpty()) {
                return ResponseEntity.status(401).build();
            }
            return ResponseEntity.ok().header("X-Customer-Id", sub).build();
        } catch (Exception e) {
            log.debug("verify: token parse failed", e);
            return ResponseEntity.status(401).build();
        }
    }
}
