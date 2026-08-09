package com.ecommerce.product.infra.admission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ecommerce.product.infra.admission.AdmissionRateLimiter.AdmissionDecision;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class AdmissionRateLimiterTest {

    @SuppressWarnings("unchecked")
    @Test
    void allowsWhenScriptReturnsTokenAvailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), any(List.class),
                anyString(), anyString(), anyString()))
                .thenReturn(List.of(1L, 0L));

        AdmissionRateLimiter limiter = new AdmissionRateLimiter(redisTemplate, 200, 400, false);
        AdmissionDecision decision = limiter.tryAcquire();

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.retryAfterSeconds()).isZero();
    }

    @SuppressWarnings("unchecked")
    @Test
    void rejectsWithRetryAfterWhenScriptReturnsExhausted() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), any(List.class),
                anyString(), anyString(), anyString()))
                .thenReturn(List.of(0L, 3L));

        AdmissionRateLimiter limiter = new AdmissionRateLimiter(redisTemplate, 200, 400, false);
        AdmissionDecision decision = limiter.tryAcquire();

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(3L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void failsClosedByDefaultWhenRedisThrows() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), any(List.class),
                anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("redis unavailable"));

        AdmissionRateLimiter limiter = new AdmissionRateLimiter(redisTemplate, 200, 400, false);
        AdmissionDecision decision = limiter.tryAcquire();

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isPositive();
    }

    @SuppressWarnings("unchecked")
    @Test
    void failsOpenWhenConfiguredAndRedisThrows() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), any(List.class),
                anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("redis unavailable"));

        AdmissionRateLimiter limiter = new AdmissionRateLimiter(redisTemplate, 200, 400, true);
        AdmissionDecision decision = limiter.tryAcquire();

        assertThat(decision.allowed()).isTrue();
    }
}
