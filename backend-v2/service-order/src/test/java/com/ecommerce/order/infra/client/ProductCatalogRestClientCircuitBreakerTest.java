package com.ecommerce.order.infra.client;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.OrderErrorCode;
import com.ecommerce.order.domain.service.ProductCatalogPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.spring6.circuitbreaker.configure.CircuitBreakerAspect;
import io.github.resilience4j.spring6.circuitbreaker.configure.CircuitBreakerConfigurationProperties;
import io.github.resilience4j.spring6.fallback.FallbackDecorators;
import io.github.resilience4j.spring6.fallback.FallbackExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ProductCatalogRestClient의 Circuit Breaker 동작 단위 테스트.
 *
 * <p>Resilience4j를 Spring Context 없이 수동 구성하고, AspectJ proxy로 @CircuitBreaker
 * annotation을 활성화한다. RestClient는 ClientHttpRequestFactory를 stub으로 주입해
 * Product 서비스 응답을 시뮬레이션한다.
 *
 * <p>검증 포인트:
 * 1. 정상 응답이 반복되면 CLOSED 상태 유지
 * 2. 연속 5xx 실패 시 OPEN으로 전이
 * 3. OPEN 상태에서는 fallback이 즉시 실행됨 (fast-fail)
 * 4. releaseStock의 fallback은 보상 재시도 판단을 위해 예외를 전파
 */
class ProductCatalogRestClientCircuitBreakerTest {

    private CircuitBreakerRegistry registry;
    private CircuitBreaker circuitBreaker;
    private StubHttpResponseFactory stubFactory;
    private ProductCatalogPort proxiedClient;

    @BeforeEach
    void setUp() {
        // 빠른 검증을 위해 slidingWindow=5로 작게 설정
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(5)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(10))
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(false)
                .recordException(ex ->
                        ex instanceof HttpServerErrorException
                                || ex instanceof org.springframework.web.client.ResourceAccessException)
                .build();

        registry = CircuitBreakerRegistry.of(config);
        circuitBreaker = registry.circuitBreaker("productService");

        stubFactory = new StubHttpResponseFactory();
        RestClient restClient = RestClient.builder()
                .baseUrl("http://product-test")
                .requestFactory(stubFactory)
                .build();

        proxiedClient = buildProxiedClient(restClient);
    }

    private ProductCatalogPort buildProxiedClient(RestClient rc) {
        ProductCatalogRestClient target = new ProductCatalogRestClient(rc);

        CircuitBreakerConfigurationProperties properties = new CircuitBreakerConfigurationProperties();
        SimpleSpelResolver spelResolver = new SimpleSpelResolver();
        FallbackDecorators fallbackDecorators = new FallbackDecorators(List.of());
        FallbackExecutor fallbackExecutor = new FallbackExecutor(spelResolver, fallbackDecorators);

        CircuitBreakerAspect aspect = new CircuitBreakerAspect(
                properties,
                registry,
                List.of(),
                fallbackExecutor,
                spelResolver
        );

        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    @Test
    @DisplayName("정상 응답이 반복되면 Circuit Breaker는 CLOSED 상태를 유지한다")
    void normalResponse_keepsCircuitClosed() {
        // 준비: 모든 호출에 대해 200 OK 응답
        stubFactory.respondWith(HttpStatus.OK, "{\"success\":true}");

        // 실행: 5번 호출
        for (int i = 0; i < 5; i++) {
            proxiedClient.existsVariant(1L);
        }

        // 검증: CLOSED 상태 유지
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("Product 서비스에서 5xx 실패가 임계치를 넘으면 Circuit Breaker가 OPEN으로 전이한다")
    void repeatedServerErrors_opensCircuit() {
        // 준비: 항상 5xx 응답
        stubFactory.respondWith(HttpStatus.INTERNAL_SERVER_ERROR, "server error");

        // 실행: 5번 reserveStock 호출 → 전부 실패
        for (int i = 0; i < 5; i++) {
            try {
                proxiedClient.reserveStock(1L, 1);
            } catch (Exception ignored) {
                // fallback이 BusinessException을 던지는 것은 정상
            }
        }

        // 검증: 실패율 100%가 50% 임계치 초과 → OPEN으로 전이
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("existsVariant는 4xx 응답을 false로 반환하고 Circuit Breaker 실패로 기록하지 않는다")
    void existsVariant_clientError_returnsFalseAndKeepsCircuitClosed() {
        // 준비
        stubFactory.respondWith(HttpStatus.NOT_FOUND, "{\"success\":false}");

        // 실행
        for (int i = 0; i < 5; i++) {
            assertThat(proxiedClient.existsVariant(404L)).isFalse();
        }

        // 검증
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("재고 부족 같은 4xx 비즈니스 실패는 Circuit Breaker를 OPEN시키지 않는다")
    void reserveStock_clientError_doesNotOpenCircuit() {
        // 준비
        stubFactory.respondWith(HttpStatus.BAD_REQUEST, "{\"success\":false}");

        // 실행
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> proxiedClient.reserveStock(1L, 1))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.STOCK_RESERVATION_FAILED);
        }

        // 검증
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("Circuit이 OPEN이면 실제 HTTP 호출 없이 fallback이 즉시 실행된다 (fast-fail)")
    void openCircuit_invokesFallbackImmediately_noHttpCall() {
        // 준비: CB를 강제로 OPEN 상태로 전환
        circuitBreaker.transitionToOpenState();
        stubFactory.respondWith(HttpStatus.OK, "{\"success\":true}");
        int invocationCountBefore = stubFactory.getInvocationCount();

        // 실행: reserveStock 호출 → fast-fail
        assertThatThrownBy(() -> proxiedClient.reserveStock(1L, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("circuit open");

        // 검증: HTTP 호출이 발생하지 않음
        assertThat(stubFactory.getInvocationCount()).isEqualTo(invocationCountBefore);
    }

    @Test
    @DisplayName("OPEN 상태의 fetchSnapshot은 PRODUCT_SERVICE_UNAVAILABLE 에러를 반환한다")
    void openCircuit_fetchSnapshot_throwsServiceUnavailable() {
        // 준비
        circuitBreaker.transitionToOpenState();

        // 실행 및 검증
        assertThatThrownBy(() -> proxiedClient.fetchSnapshot(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("OPEN 상태의 releaseStock은 보상 재시도 판단을 위해 PRODUCT_SERVICE_UNAVAILABLE을 전파한다")
    void openCircuit_releaseStock_throwsServiceUnavailable() {
        // 준비
        circuitBreaker.transitionToOpenState();

        // 실행 및 검증
        assertThatThrownBy(() -> proxiedClient.releaseStock(1L, 1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE);
    }

    // 테스트 헬퍼

    /**
     * ClientHttpRequestFactory 대역 객체. 설정된 상태 코드와 본문으로 응답한다.
     */
    private static class StubHttpResponseFactory
            implements org.springframework.http.client.ClientHttpRequestFactory {
        private HttpStatus status = HttpStatus.OK;
        private String body = "";
        private int invocationCount = 0;

        void respondWith(HttpStatus status, String body) {
            this.status = status;
            this.body = body;
        }

        int getInvocationCount() {
            return invocationCount;
        }

        @Override
        public org.springframework.http.client.ClientHttpRequest createRequest(
                URI uri, org.springframework.http.HttpMethod httpMethod) {
            invocationCount++;
            return new org.springframework.mock.http.client.MockClientHttpRequest(httpMethod, uri) {
                @Override
                protected org.springframework.http.client.ClientHttpResponse executeInternal() {
                    MockClientHttpResponse response = new MockClientHttpResponse(body.getBytes(), status);
                    response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                    return response;
                }
            };
        }
    }

    /**
     * FallbackExecutor에 필요한 최소 SpelResolver 구현 — 이 테스트에서는 SpEL 미사용.
     */
    private static class SimpleSpelResolver
            implements io.github.resilience4j.spring6.spelresolver.SpelResolver {
        @Override
        public String resolve(java.lang.reflect.Method method, Object[] arguments, String spelExpression) {
            return spelExpression;
        }
    }
}
