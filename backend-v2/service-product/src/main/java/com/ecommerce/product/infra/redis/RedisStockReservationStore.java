package com.ecommerce.product.infra.redis;

import com.ecommerce.product.application.service.StockReservationStore;
import java.util.List;
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
            local orderId = ARGV[1]
            local quantity = tonumber(ARGV[2])
            local availableStock = tonumber(ARGV[3])
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

    private final StringRedisTemplate redisTemplate;

    public RedisStockReservationStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean reserve(Long variantId, Long orderId, int quantity, int availableStock) {
        Long result = redisTemplate.execute(
                RESERVE_SCRIPT,
                List.of(reservationsKey(variantId), totalKey(variantId)),
                orderId.toString(),
                Integer.toString(quantity),
                Integer.toString(availableStock)
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

    private String reservationsKey(Long variantId) {
        return "stock:reservation:" + variantId + ":orders";
    }

    private String totalKey(Long variantId) {
        return "stock:reservation:" + variantId + ":total";
    }
}
