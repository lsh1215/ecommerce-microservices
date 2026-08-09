package com.ecommerce.product.infra.redis;

import com.ecommerce.product.domain.model.StockReservation;
import com.ecommerce.product.domain.repository.StockReservationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.RedisListCommands.Direction;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the durable settle queue that {@link RedisStockReservationStore}'s reserve
 * Lua script populates atomically alongside the Redis admit, and materializes the
 * corresponding {@code stock_reservation} rows in the DB off the synchronous reserve
 * path (see {@code reserve.settle.mode=async} on {@code ProductService}).
 *
 * <p>Reliable-queue pattern: items are LMOVE'd from the per-variant settle list into
 * a per-variant "processing" list before being persisted. A crash between the move
 * and the DB insert loses nothing observable — the item simply stays on the
 * processing list and is retried on the next tick, instead of vanishing. Once an
 * item is confirmed materialized (inserted, or already present from a prior/racing
 * drain) it is removed from the processing list; a transient failure (e.g. DB/Redis
 * unavailable) is retried in place up to {@code reserve.settle.max-attempts} times
 * before being routed to a per-variant DLQ, while a poison item (fails to parse) is
 * routed to the DLQ immediately.
 *
 * <p>Only active while {@code reserve.settle.mode=async}: in the default {@code sync}
 * mode the reserve Lua never enqueues onto the settle queue, so this bean is not even
 * registered (see {@link ConditionalOnProperty}), matching the {@code !test} profile
 * gating already applied to {@link RedisStockReservationStore}'s writer side (see
 * {@link InMemoryStockReservationStore}, which never populates these queues).
 */
@Component
@Slf4j
@Profile("!test")
@ConditionalOnProperty(name = "reserve.settle.mode", havingValue = "async")
public class StockReservationSettler {

    private static final DefaultRedisScript<Long> CLEANUP_SCRIPT = new DefaultRedisScript<>("""
            local settleKey = KEYS[1]
            local processingKey = KEYS[2]
            local activeVariantsKey = KEYS[3]
            local variantId = ARGV[1]
            if redis.call('LLEN', settleKey) == 0 and redis.call('LLEN', processingKey) == 0 then
              redis.call('SREM', activeVariantsKey, variantId)
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final StockReservationRepository stockReservationRepository;
    private final int batchSize;
    private final int maxAttempts;

    private final AtomicLong backlogDepth = new AtomicLong(0);
    private final AtomicLong dlqDepth = new AtomicLong(0);
    private final Counter settledCounter;

    public StockReservationSettler(StringRedisTemplate redisTemplate,
                                    StockReservationRepository stockReservationRepository,
                                    MeterRegistry meterRegistry,
                                    @Value("${reserve.settle.batch-size:200}") int batchSize,
                                    @Value("${reserve.settle.max-attempts:5}") int maxAttempts) {
        this.redisTemplate = redisTemplate;
        this.stockReservationRepository = stockReservationRepository;
        this.batchSize = Math.max(1, batchSize);
        this.maxAttempts = Math.max(1, maxAttempts);

        Gauge.builder("stock_reservation_settle_backlog", backlogDepth, AtomicLong::doubleValue)
                .description("stock_reservation rows queued for async settlement across all active variants")
                .register(meterRegistry);
        Gauge.builder("stock_reservation_settle_dlq", dlqDepth, AtomicLong::doubleValue)
                .description("Settle items routed to the DLQ after a DB insert failure")
                .register(meterRegistry);
        this.settledCounter = Counter.builder("stock_reservation_settle_settled_total")
                .description("stock_reservation rows materialized to the DB by the async settler")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${reserve.settle.fixed-delay-ms:200}")
    public void drain() {
        Set<String> variantIds = redisTemplate.opsForSet().members(RedisReservationKeys.ACTIVE_VARIANTS_KEY);
        if (variantIds == null || variantIds.isEmpty()) {
            backlogDepth.set(0);
            dlqDepth.set(0);
            return;
        }
        long backlog = 0;
        long dlq = 0;
        for (String variantIdText : variantIds) {
            try {
                Long variantId = Long.valueOf(variantIdText);
                drainVariant(variantId);
                backlog += size(RedisReservationKeys.settleQueueKey(variantId))
                        + size(RedisReservationKeys.processingKey(variantId));
                dlq += size(RedisReservationKeys.settleDlqKey(variantId));
            } catch (Exception e) {
                log.error("stock_reservation settle 드레인 실패, 다음 variant로 계속 진행: variantId={}", variantIdText, e);
            }
        }
        backlogDepth.set(backlog);
        dlqDepth.set(dlq);
    }

    private void drainVariant(Long variantId) {
        String settleKey = RedisReservationKeys.settleQueueKey(variantId);
        String processingKey = RedisReservationKeys.processingKey(variantId);
        String dlqKey = RedisReservationKeys.settleDlqKey(variantId);
        ListOperations<String, String> listOps = redisTemplate.opsForList();

        for (int i = 0; i < batchSize; i++) {
            String moved = listOps.move(settleKey, Direction.LEFT, processingKey, Direction.RIGHT);
            if (moved == null) {
                break;
            }
        }

        List<String> pending = listOps.range(processingKey, 0, -1);
        if (pending != null && !pending.isEmpty()) {
            for (String item : pending) {
                settleOne(variantId, item, processingKey, dlqKey, listOps);
            }
        }
        cleanupIfDrained(variantId, settleKey, processingKey);
    }

    /**
     * Race-safe cleanup: SREMs the variantId from the global active-variants set only
     * when both its settle and processing lists are empty, in a single Lua round trip
     * so a concurrent reserve() that just re-SADD'd the variant is never undone.
     */
    private void cleanupIfDrained(Long variantId, String settleKey, String processingKey) {
        redisTemplate.execute(
                CLEANUP_SCRIPT,
                List.of(settleKey, processingKey, RedisReservationKeys.ACTIVE_VARIANTS_KEY),
                variantId.toString()
        );
    }

    private void settleOne(Long variantId, String item, String processingKey, String dlqKey,
                            ListOperations<String, String> listOps) {
        try {
            boolean inserted = materialize(variantId, item);
            listOps.remove(processingKey, 1, item);
            clearAttempts(variantId, item);
            if (inserted) {
                settledCounter.increment();
            }
        } catch (NumberFormatException | IndexOutOfBoundsException poison) {
            // Malformed "orderId:quantity" payload can never succeed on retry — drop straight to DLQ.
            log.error("stock_reservation settle 포이즌 아이템, 즉시 DLQ로 이동: variantId={}, item={}", variantId, item, poison);
            listOps.remove(processingKey, 1, item);
            listOps.rightPush(dlqKey, item);
            clearAttempts(variantId, item);
        } catch (Exception transientFailure) {
            long attempts = incrementAttempts(variantId, item);
            if (attempts >= maxAttempts) {
                log.warn("stock_reservation settle 재시도 한도 초과({}회), DLQ로 이동: variantId={}, item={}",
                        attempts, variantId, item, transientFailure);
                listOps.remove(processingKey, 1, item);
                listOps.rightPush(dlqKey, item);
                clearAttempts(variantId, item);
            } else {
                log.warn("stock_reservation settle 일시 실패({}/{}), 다음 tick에 재시도: variantId={}, item={}",
                        attempts, maxAttempts, variantId, item, transientFailure);
                // Left in the processing list on purpose — retried on the next drain tick.
            }
        }
    }

    private long incrementAttempts(Long variantId, String item) {
        Long attempts = redisTemplate.opsForHash()
                .increment(RedisReservationKeys.settleAttemptsKey(variantId), item, 1L);
        return attempts == null ? 1L : attempts;
    }

    private void clearAttempts(Long variantId, String item) {
        redisTemplate.opsForHash().delete(RedisReservationKeys.settleAttemptsKey(variantId), item);
    }

    /**
     * Inserts the {@code stock_reservation} row for a queued {@code orderId:quantity}
     * item, tolerating both an already-materialized row (prior/racing drain, or a
     * concurrent sync-mode save) and a raw DB unique-constraint violation as
     * idempotent no-ops rather than failures.
     *
     * @return {@code true} only when this call performed a genuine new DB insert.
     */
    boolean materialize(Long variantId, String item) {
        int sep = item.lastIndexOf(':');
        Long orderId = Long.valueOf(item.substring(0, sep));
        int quantity = Integer.parseInt(item.substring(sep + 1));

        if (stockReservationRepository.findByOrderIdAndVariantId(orderId, variantId).isPresent()) {
            return false;
        }
        try {
            stockReservationRepository.save(StockReservation.reserve(orderId, variantId, quantity));
            return true;
        } catch (DataIntegrityViolationException alreadyExists) {
            // Row materialized concurrently (racing drain, or a sync-mode save) — idempotent no-op.
            return false;
        }
    }

    private long size(String key) {
        Long len = redisTemplate.opsForList().size(key);
        return len == null ? 0 : len;
    }
}
