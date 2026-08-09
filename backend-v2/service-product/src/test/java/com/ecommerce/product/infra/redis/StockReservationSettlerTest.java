package com.ecommerce.product.infra.redis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.product.domain.model.StockReservation;
import com.ecommerce.product.domain.repository.StockReservationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.RedisListCommands.Direction;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class StockReservationSettlerTest {

    private static final Long VARIANT_ID = 5L;
    private static final Long ORDER_ID = 100L;
    private static final String ITEM = ORDER_ID + ":3";
    private static final int MAX_ATTEMPTS = 3;

    private StringRedisTemplate redisTemplate;
    private ListOperations<String, String> listOps;
    private SetOperations<String, String> setOps;
    private HashOperations<String, Object, Object> hashOps;
    private StockReservationRepository stockReservationRepository;
    private StockReservationSettler settler;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        listOps = mock(ListOperations.class);
        setOps = mock(SetOperations.class);
        hashOps = mock(HashOperations.class);
        stockReservationRepository = mock(StockReservationRepository.class);

        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(setOps.members(RedisReservationKeys.ACTIVE_VARIANTS_KEY)).thenReturn(Set.of(VARIANT_ID.toString()));
        // no further backlog after the single item is drained
        when(listOps.size(RedisReservationKeys.settleQueueKey(VARIANT_ID))).thenReturn(0L);
        when(listOps.size(RedisReservationKeys.processingKey(VARIANT_ID))).thenReturn(0L);
        when(listOps.size(RedisReservationKeys.settleDlqKey(VARIANT_ID))).thenReturn(0L);

        settler = new StockReservationSettler(redisTemplate, stockReservationRepository,
                new SimpleMeterRegistry(), 10, MAX_ATTEMPTS);
    }

    private void noMoreMoves() {
        when(listOps.move(eq(RedisReservationKeys.settleQueueKey(VARIANT_ID)), eq(Direction.LEFT),
                eq(RedisReservationKeys.processingKey(VARIANT_ID)), eq(Direction.RIGHT)))
                .thenReturn(null);
    }

    @Test
    void drainMaterializesQueuedItemExactlyOnce() {
        // First LMOVE returns the item, subsequent calls signal an empty queue.
        when(listOps.move(eq(RedisReservationKeys.settleQueueKey(VARIANT_ID)), eq(Direction.LEFT),
                eq(RedisReservationKeys.processingKey(VARIANT_ID)), eq(Direction.RIGHT)))
                .thenReturn(ITEM, (String) null);
        when(listOps.range(RedisReservationKeys.processingKey(VARIANT_ID), 0, -1)).thenReturn(List.of(ITEM));
        when(stockReservationRepository.findByOrderIdAndVariantId(ORDER_ID, VARIANT_ID)).thenReturn(Optional.empty());

        settler.drain();

        verify(stockReservationRepository, times(1)).save(argThatReservation());
        verify(listOps).remove(RedisReservationKeys.processingKey(VARIANT_ID), 1, ITEM);
        verify(hashOps).delete(RedisReservationKeys.settleAttemptsKey(VARIANT_ID), ITEM);
    }

    @Test
    void reDrainOfAnAlreadyMaterializedItemDoesNotDuplicate() {
        // Simulates a crash after the DB insert but before the item was removed from
        // the processing list: the next drain tick finds the same item still pending.
        noMoreMoves();
        when(listOps.range(RedisReservationKeys.processingKey(VARIANT_ID), 0, -1)).thenReturn(List.of(ITEM));
        when(stockReservationRepository.findByOrderIdAndVariantId(ORDER_ID, VARIANT_ID))
                .thenReturn(Optional.of(StockReservation.reserve(ORDER_ID, VARIANT_ID, 3)));

        settler.drain();

        verify(stockReservationRepository, never()).save(argThatReservation());
        verify(listOps).remove(RedisReservationKeys.processingKey(VARIANT_ID), 1, ITEM);
    }

    @Test
    void concurrentUniqueConstraintViolationIsTreatedAsIdempotentSuccessNotDlq() {
        noMoreMoves();
        when(listOps.range(RedisReservationKeys.processingKey(VARIANT_ID), 0, -1)).thenReturn(List.of(ITEM));
        when(stockReservationRepository.findByOrderIdAndVariantId(ORDER_ID, VARIANT_ID)).thenReturn(Optional.empty());
        when(stockReservationRepository.save(argThatReservation()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        settler.drain();

        // removed from processing (materialize treated the race as success) — never pushed to DLQ.
        verify(listOps).remove(RedisReservationKeys.processingKey(VARIANT_ID), 1, ITEM);
        verify(listOps, never()).rightPush(eq(RedisReservationKeys.settleDlqKey(VARIANT_ID)), anyString());
    }

    @Test
    void transientFailureOnFirstAttemptLeavesItemInProcessingAndIncrementsAttempts() {
        noMoreMoves();
        when(listOps.range(RedisReservationKeys.processingKey(VARIANT_ID), 0, -1)).thenReturn(List.of(ITEM));
        when(stockReservationRepository.findByOrderIdAndVariantId(ORDER_ID, VARIANT_ID))
                .thenThrow(new RuntimeException("DB unavailable"));
        when(hashOps.increment(RedisReservationKeys.settleAttemptsKey(VARIANT_ID), ITEM, 1L)).thenReturn(1L);

        settler.drain();

        verify(listOps, never()).remove(RedisReservationKeys.processingKey(VARIANT_ID), 1, ITEM);
        verify(listOps, never()).rightPush(eq(RedisReservationKeys.settleDlqKey(VARIANT_ID)), anyString());
        verify(hashOps).increment(RedisReservationKeys.settleAttemptsKey(VARIANT_ID), ITEM, 1L);
    }

    @Test
    void transientFailureRoutesToDlqOnceMaxAttemptsReached() {
        noMoreMoves();
        when(listOps.range(RedisReservationKeys.processingKey(VARIANT_ID), 0, -1)).thenReturn(List.of(ITEM));
        when(stockReservationRepository.findByOrderIdAndVariantId(ORDER_ID, VARIANT_ID))
                .thenThrow(new RuntimeException("DB unavailable"));
        when(hashOps.increment(RedisReservationKeys.settleAttemptsKey(VARIANT_ID), ITEM, 1L))
                .thenReturn((long) MAX_ATTEMPTS);

        settler.drain();

        verify(listOps).remove(RedisReservationKeys.processingKey(VARIANT_ID), 1, ITEM);
        verify(listOps).rightPush(RedisReservationKeys.settleDlqKey(VARIANT_ID), ITEM);
        verify(hashOps).delete(RedisReservationKeys.settleAttemptsKey(VARIANT_ID), ITEM);
    }

    @Test
    void poisonItemThatFailsToParseIsRoutedToDlqImmediately() {
        String poisonItem = "not-a-number:3";
        noMoreMoves();
        when(listOps.range(RedisReservationKeys.processingKey(VARIANT_ID), 0, -1)).thenReturn(List.of(poisonItem));

        settler.drain();

        verify(listOps).remove(RedisReservationKeys.processingKey(VARIANT_ID), 1, poisonItem);
        verify(listOps).rightPush(RedisReservationKeys.settleDlqKey(VARIANT_ID), poisonItem);
        verify(hashOps).delete(RedisReservationKeys.settleAttemptsKey(VARIANT_ID), poisonItem);
        verify(stockReservationRepository, never()).findByOrderIdAndVariantId(any(), any());
    }

    @Test
    void noColonPoisonItemIsRoutedToDlqImmediately() {
        String poisonItem = "999";
        noMoreMoves();
        when(listOps.range(RedisReservationKeys.processingKey(VARIANT_ID), 0, -1)).thenReturn(List.of(poisonItem));

        settler.drain();

        verify(listOps).remove(RedisReservationKeys.processingKey(VARIANT_ID), 1, poisonItem);
        verify(listOps).rightPush(RedisReservationKeys.settleDlqKey(VARIANT_ID), poisonItem);
        verify(hashOps).delete(RedisReservationKeys.settleAttemptsKey(VARIANT_ID), poisonItem);
        verify(stockReservationRepository, never()).findByOrderIdAndVariantId(any(), any());
    }

    @Test
    void drainSkipsMalformedActiveVariantMemberAndStillProcessesTheValidOne() {
        Long otherVariantId = 9L;
        when(setOps.members(RedisReservationKeys.ACTIVE_VARIANTS_KEY))
                .thenReturn(Set.of("not-a-number", otherVariantId.toString()));
        when(listOps.size(RedisReservationKeys.settleQueueKey(otherVariantId))).thenReturn(0L);
        when(listOps.size(RedisReservationKeys.processingKey(otherVariantId))).thenReturn(0L);
        when(listOps.size(RedisReservationKeys.settleDlqKey(otherVariantId))).thenReturn(0L);

        String otherItem = "200:4";
        when(listOps.move(eq(RedisReservationKeys.settleQueueKey(otherVariantId)), eq(Direction.LEFT),
                eq(RedisReservationKeys.processingKey(otherVariantId)), eq(Direction.RIGHT)))
                .thenReturn(otherItem, (String) null);
        when(listOps.range(RedisReservationKeys.processingKey(otherVariantId), 0, -1))
                .thenReturn(List.of(otherItem));
        when(stockReservationRepository.findByOrderIdAndVariantId(200L, otherVariantId))
                .thenReturn(Optional.empty());

        settler.drain();

        verify(stockReservationRepository).save(argThatReservation());
        verify(listOps).remove(RedisReservationKeys.processingKey(otherVariantId), 1, otherItem);
    }

    @Test
    void drainVariantTriggersActiveVariantsCleanupWhenQueuesAreEmpty() {
        noMoreMoves();
        when(listOps.range(RedisReservationKeys.processingKey(VARIANT_ID), 0, -1)).thenReturn(List.of());

        settler.drain();

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(RedisReservationKeys.settleQueueKey(VARIANT_ID),
                        RedisReservationKeys.processingKey(VARIANT_ID),
                        RedisReservationKeys.ACTIVE_VARIANTS_KEY)),
                eq(VARIANT_ID.toString())
        );
    }

    private static StockReservation argThatReservation() {
        return org.mockito.ArgumentMatchers.any(StockReservation.class);
    }
}
