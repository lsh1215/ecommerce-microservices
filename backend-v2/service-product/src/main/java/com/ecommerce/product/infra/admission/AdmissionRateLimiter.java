package com.ecommerce.product.infra.admission;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 토큰 버킷 admission gate (레이어 1).
 *
 * <p>플래시 세일 스파이크가 DB reserve 경로에 닿기 전에 서비스 앞단에서 걸러낸다.
 * 버킷 상태는 단일 Redis hash 키(tokens, ts)에 저장하고, Redis {@code TIME}을 클럭으로
 * 사용해 여러 인스턴스가 동시에 접근해도 Lua 스크립트 원자성으로 경쟁 조건을 막는다.
 *
 * <p>FAIL-CLOSED: limiter store(Redis)에 문제가 생겨 판단할 수 없으면 기본적으로
 * 요청을 거부한다({@code admission.fail-open=false}). 이는 admission gate가 있는데도
 * 장애 시 무제한 통과시켜 버리는 것을 막기 위함이다.
 */
@Component
@Profile("!test")
public class AdmissionRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AdmissionRateLimiter.class);

    private static final String BUCKET_KEY = "admission:reserve:bucket";
    private static final long BUCKET_TTL_SECONDS = 60;
    private static final long FAIL_CLOSED_RETRY_AFTER_SECONDS = 1;

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> TOKEN_BUCKET_SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local rate = tonumber(ARGV[1])
            local burst = tonumber(ARGV[2])
            local ttl = tonumber(ARGV[3])
            local time = redis.call('TIME')
            local now = tonumber(time[1]) + tonumber(time[2]) / 1000000
            local tokens = tonumber(redis.call('HGET', key, 'tokens'))
            local ts = tonumber(redis.call('HGET', key, 'ts'))
            if tokens == nil or ts == nil then
              tokens = burst
              ts = now
            end
            local elapsed = now - ts
            if elapsed < 0 then
              elapsed = 0
            end
            local refill = elapsed * rate
            tokens = math.min(burst, tokens + refill)
            local allowed = 0
            local retryAfter = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            else
              retryAfter = math.ceil((1 - tokens) / rate)
            end
            redis.call('HSET', key, 'tokens', tostring(tokens), 'ts', tostring(now))
            redis.call('EXPIRE', key, ttl)
            return {allowed, retryAfter}
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final double rate;
    private final long burst;
    private final boolean failOpen;

    public AdmissionRateLimiter(StringRedisTemplate redisTemplate,
                                 @Value("${admission.rate:200}") double rate,
                                 @Value("${admission.burst:400}") long burst,
                                 @Value("${admission.fail-open:false}") boolean failOpen) {
        this.redisTemplate = redisTemplate;
        this.rate = rate;
        this.burst = burst;
        this.failOpen = failOpen;
    }

    @SuppressWarnings("unchecked")
    public AdmissionDecision tryAcquire() {
        try {
            List<Long> result = redisTemplate.execute(
                    TOKEN_BUCKET_SCRIPT,
                    List.of(BUCKET_KEY),
                    Double.toString(rate),
                    Long.toString(burst),
                    Long.toString(BUCKET_TTL_SECONDS)
            );
            if (result == null || result.size() < 2) {
                return failClosedOrOpen();
            }
            boolean allowed = result.get(0) != null && result.get(0) == 1L;
            long retryAfterSeconds = result.get(1) == null ? 0L : result.get(1);
            return new AdmissionDecision(allowed, retryAfterSeconds);
        } catch (Exception e) {
            log.warn("[Admission] token bucket check failed ({}), defaulting to fail-{}: {}",
                    e.getClass().getSimpleName(), failOpen ? "open" : "closed", e.getMessage());
            return failClosedOrOpen();
        }
    }

    private AdmissionDecision failClosedOrOpen() {
        return failOpen
                ? new AdmissionDecision(true, 0)
                : new AdmissionDecision(false, FAIL_CLOSED_RETRY_AFTER_SECONDS);
    }

    public record AdmissionDecision(boolean allowed, long retryAfterSeconds) {
    }
}
