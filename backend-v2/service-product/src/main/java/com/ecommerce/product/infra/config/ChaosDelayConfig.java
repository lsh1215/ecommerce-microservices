package com.ecommerce.product.infra.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Chaos Engineering 지연 주입 설정 (Phase 4 테스트 전용).
 *
 * <p>`app.chaos.enabled=true` 이면 Internal 엔드포인트(/api/internal/products/**)에
 * 인위적 지연을 추가한다. Circuit Breaker의 slow call detection을 검증하기 위함.
 *
 * <p>운영에서는 절대 활성화하지 않음.
 */
@Configuration
@ConditionalOnProperty(name = "app.chaos.enabled", havingValue = "true")
public class ChaosDelayConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(ChaosDelayConfig.class);

    @Value("${app.chaos.stock-delay-ms:0}")
    private long stockDelayMs;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.warn("[CHAOS] Chaos delay interceptor ENABLED: stockDelayMs={}", stockDelayMs);
        registry.addInterceptor(new ChaosDelayInterceptor(stockDelayMs))
                .addPathPatterns("/api/internal/products/**");
    }

    private static class ChaosDelayInterceptor implements HandlerInterceptor {
        private final long delayMs;

        ChaosDelayInterceptor(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return true;
        }
    }
}
