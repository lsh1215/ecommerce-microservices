package com.ecommerce.product.infra.redis;

import com.ecommerce.product.application.service.StockReservationStore;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RedisStockReservationStore implements StockReservationStore {

    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            local reservationsKey = KEYS[1]
            local totalKey = KEYS[2]
            local settleQueueKey = KEYS[3]
            local activeVariantsKey = KEYS[4]
            local orderId = ARGV[1]
            local quantity = tonumber(ARGV[2])
            local availableStock = tonumber(ARGV[3])
            local variantId = ARGV[4]
            local enqueue = ARGV[5]
            local existing = redis.call('HGET', reservationsKey, orderId)
            if existing then
              return 2
            end
            local reservedTotal = tonumber(redis.call('GET', totalKey) or '0')
            if reservedTotal + quantity > availableStock then
              return 0
            end
            redis.call('HSET', reservationsKey, orderId, quantity)
            redis.call('INCRBY', totalKey, quantity)
            if enqueue == '1' then
              redis.call('RPUSH', settleQueueKey, orderId .. ':' .. quantity)
              redis.call('SADD', activeVariantsKey, variantId)
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            local reservationsKey = KEYS[1]
            local totalKey = KEYS[2]
            local orderId = ARGV[1]
            local quantity = redis.call('HGET', reservationsKey, orderId)
            if not quantity then
              return 0
            end
            redis.call('HDEL', reservationsKey, orderId)
            redis.call('DECRBY', totalKey, tonumber(quantity))
            return tonumber(quantity)
            """, Long.class);

    /**
     * Pure Redis admit path for async settle mode: no DB reads or writes. Requires the
     * available-stock snapshot to have been preloaded via {@link #preloadAvailable} first
     * (KEYS[5]); otherwise returns -1 so the caller can fall back to the DB-backed path.
     * Dedup/capacity/admit logic mirrors {@link #RESERVE_SCRIPT}, but this path always
     * enqueues the settle item (it is only ever used in async settle mode).
     */
    private static final DefaultRedisScript<Long> RESERVE_REDIS_ONLY_SCRIPT = new DefaultRedisScript<>("""
            local reservationsKey = KEYS[1]
            local totalKey = KEYS[2]
            local settleQueueKey = KEYS[3]
            local activeVariantsKey = KEYS[4]
            local availableKey = KEYS[5]
            local orderId = ARGV[1]
            local quantity = tonumber(ARGV[2])
            local variantId = ARGV[3]
            local avail = redis.call('GET', availableKey)
            if not avail then
              return -1
            end
            local existing = redis.call('HGET', reservationsKey, orderId)
            if existing then
              return 2
            end
            local reservedTotal = tonumber(redis.call('GET', totalKey) or '0')
            if reservedTotal + quantity > tonumber(avail) then
              return 0
            end
            redis.call('HSET', reservationsKey, orderId, quantity)
            redis.call('INCRBY', totalKey, quantity)
            redis.call('RPUSH', settleQueueKey, orderId .. ':' .. quantity)
            redis.call('SADD', activeVariantsKey, variantId)
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String settleMode;
    private final boolean enqueueSettle;

    public RedisStockReservationStore(StringRedisTemplate redisTemplate,
                                       @Value("${reserve.settle.mode:sync}") String settleMode) {
        this.redisTemplate = redisTemplate;
        this.settleMode = settleMode;
        this.enqueueSettle = "async".equalsIgnoreCase(settleMode);
    }

    @Override
    public boolean reserve(Long variantId, Long orderId, int quantity, int availableStock) {
        Long result = redisTemplate.execute(
                RESERVE_SCRIPT,
                List.of(reservationsKey(variantId), totalKey(variantId),
                        settleQueueKey(variantId), RedisReservationKeys.ACTIVE_VARIANTS_KEY),
                orderId.toString(),
                Integer.toString(quantity),
                Integer.toString(availableStock),
                variantId.toString(),
                enqueueSettle ? "1" : "0"
        );
        return result != null && result > 0;
    }

    @Override
    public void release(Long variantId, Long orderId) {
        redisTemplate.execute(
                RELEASE_SCRIPT,
                List.of(reservationsKey(variantId), totalKey(variantId)),
                orderId.toString()
        );
    }

    @Override
    public Optional<Integer> findReservedQuantity(Long variantId, Long orderId) {
        Object quantity = redisTemplate.opsForHash().get(reservationsKey(variantId), orderId.toString());
        if (quantity == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(quantity.toString()));
        } catch (NumberFormatException corrupted) {
            // Only the internal RESERVE_SCRIPT writes this field (always an integer); a
            // corrupted value degrades to "no reservation" rather than a 500.
            return Optional.empty();
        }
    }

    @Override
    public void preloadAvailable(Long variantId, long available) {
        redisTemplate.opsForValue().set(availableKey(variantId), Long.toString(available));
    }

    @Override
    public int reserveRedisOnly(Long variantId, Long orderId, int quantity) {
        Long result = redisTemplate.execute(
                RESERVE_REDIS_ONLY_SCRIPT,
                List.of(reservationsKey(variantId), totalKey(variantId),
                        settleQueueKey(variantId), RedisReservationKeys.ACTIVE_VARIANTS_KEY,
                        availableKey(variantId)),
                orderId.toString(),
                Integer.toString(quantity),
                variantId.toString()
        );
        return result == null ? -1 : result.intValue();
    }

    private String reservationsKey(Long variantId) {
        return RedisReservationKeys.reservationsKey(variantId);
    }

    private String totalKey(Long variantId) {
        return RedisReservationKeys.totalKey(variantId);
    }

    private String settleQueueKey(Long variantId) {
        return RedisReservationKeys.settleQueueKey(variantId);
    }

    private String availableKey(Long variantId) {
        return RedisReservationKeys.availableKey(variantId);
    }
}
