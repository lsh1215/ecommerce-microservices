package com.ecommerce.product.infra.config;

import com.ecommerce.product.api.filter.AdmissionInterceptor;
import com.ecommerce.product.infra.admission.AdmissionRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Admission gate 배선 (레이어 1: 서비스 앞단에서 스파이크를 차단).
 *
 * <p>{@code /api/internal/products/variants/{variantId}/reserve-stock}에만 적용한다.
 * release-stock, confirm-reservation은 보상/완료 경로이므로 절대 rate-limit하지 않는다.
 *
 * <p>{@link AdmissionRateLimiter}가 {@code @Profile("!test")}이므로 이 설정도 동일 프로파일로
 * 묶어 슬라이스 테스트가 Redis 없이 컨텍스트를 띄울 수 있게 한다.
 */
@Configuration
@Profile("!test")
public class AdmissionConfig implements WebMvcConfigurer {

    private static final String RESERVE_STOCK_PATH =
            "/api/internal/products/variants/*/reserve-stock";

    private final AdmissionRateLimiter admissionRateLimiter;

    @Value("${admission.mode:off}")
    private String mode;

    public AdmissionConfig(AdmissionRateLimiter admissionRateLimiter) {
        this.admissionRateLimiter = admissionRateLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdmissionInterceptor(admissionRateLimiter, mode))
                .addPathPatterns(RESERVE_STOCK_PATH);
    }
}
