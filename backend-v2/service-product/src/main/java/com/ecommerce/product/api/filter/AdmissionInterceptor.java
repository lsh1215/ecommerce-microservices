package com.ecommerce.product.api.filter;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.product.infra.admission.AdmissionRateLimiter;
import com.ecommerce.product.infra.admission.AdmissionRateLimiter.AdmissionDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Reserve-stock 진입점에 붙는 admission gate.
 *
 * <p>{@code admission.mode=on}일 때만 활성화된다(기본 off = 오늘과 동일한 무제한 통과).
 * on일 때 토큰 버킷이 소진되면 429 + Retry-After로 요청을 즉시 거부한다 — 이 요청은
 * DB reserve 경로에 도달하지 못한다.
 */
public class AdmissionInterceptor implements HandlerInterceptor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AdmissionRateLimiter admissionRateLimiter;
    private final String mode;

    public AdmissionInterceptor(AdmissionRateLimiter admissionRateLimiter, String mode) {
        this.admissionRateLimiter = admissionRateLimiter;
        this.mode = mode;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!"on".equalsIgnoreCase(mode)) {
            return true;
        }

        AdmissionDecision decision = admissionRateLimiter.tryAcquire();
        if (decision.allowed()) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(
                ApiResponse.error("System busy, please retry shortly")));
        return false;
    }
}
