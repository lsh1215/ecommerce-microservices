package com.ecommerce.product.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.ValueOperations;

class RedisStockReservationStoreTest {

    @SuppressWarnings("unchecked")
    @Test
    void reservePassesEnqueueFlagOneWhenSettleModeIsAsync() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ArgumentCaptor<String> flagCaptor = ArgumentCaptor.forClass(String.class);
        when(redisTemplate.<Long>execute(any(RedisScript.class), any(List.class),
                anyString(), anyString(), anyString(), anyString(), flagCaptor.capture()))
                .thenReturn(1L);

        RedisStockReservationStore store = new RedisStockReservationStore(redisTemplate, "async");
        boolean result = store.reserve(5L, 100L, 3, 10);

        assertThat(result).isTrue();
        assertThat(flagCaptor.getValue()).isEqualTo("1");
    }

    @SuppressWarnings("unchecked")
    @Test
    void reservePassesEnqueueFlagZeroWhenSettleModeIsSync() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ArgumentCaptor<String> flagCaptor = ArgumentCaptor.forClass(String.class);
        when(redisTemplate.<Long>execute(any(RedisScript.class), any(List.class),
                anyString(), anyString(), anyString(), anyString(), flagCaptor.capture()))
                .thenReturn(1L);

        RedisStockReservationStore store = new RedisStockReservationStore(redisTemplate, "sync");
        boolean result = store.reserve(5L, 100L, 3, 10);

        assertThat(result).isTrue();
        assertThat(flagCaptor.getValue()).isEqualTo("0");
    }

    @SuppressWarnings("unchecked")
    @Test
    void findReservedQuantityReturnsPresentEmptyAndDegradesOnCorruptValue() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        org.springframework.data.redis.core.HashOperations<String, Object, Object> hashOps =
                mock(org.springframework.data.redis.core.HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        RedisStockReservationStore store = new RedisStockReservationStore(redisTemplate, "async");

        when(hashOps.get(RedisReservationKeys.reservationsKey(5L), "100")).thenReturn("3");
        assertThat(store.findReservedQuantity(5L, 100L)).contains(3);

        when(hashOps.get(RedisReservationKeys.reservationsKey(5L), "200")).thenReturn(null);
        assertThat(store.findReservedQuantity(5L, 200L)).isEmpty();

        when(hashOps.get(RedisReservationKeys.reservationsKey(5L), "300")).thenReturn("corrupted");
        assertThat(store.findReservedQuantity(5L, 300L)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void reserveRedisOnlyPassesAvailableKeyAndArgsAndReturnsScriptCode() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> orderIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> quantityCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> variantIdCaptor = ArgumentCaptor.forClass(String.class);
        when(redisTemplate.<Long>execute(any(RedisScript.class), keysCaptor.capture(),
                orderIdCaptor.capture(), quantityCaptor.capture(), variantIdCaptor.capture()))
                .thenReturn(1L);

        RedisStockReservationStore store = new RedisStockReservationStore(redisTemplate, "async");
        int code = store.reserveRedisOnly(5L, 100L, 3);

        assertThat(code).isEqualTo(1);
        assertThat(keysCaptor.getValue()).containsExactly(
                RedisReservationKeys.reservationsKey(5L),
                RedisReservationKeys.totalKey(5L),
                RedisReservationKeys.settleQueueKey(5L),
                RedisReservationKeys.ACTIVE_VARIANTS_KEY,
                RedisReservationKeys.availableKey(5L));
        assertThat(orderIdCaptor.getValue()).isEqualTo("100");
        assertThat(quantityCaptor.getValue()).isEqualTo("3");
        assertThat(variantIdCaptor.getValue()).isEqualTo("5");
    }

    @SuppressWarnings("unchecked")
    @Test
    void reserveRedisOnlyReturnsNegativeOneWhenNotPreloaded() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.<Long>execute(any(RedisScript.class), any(List.class),
                anyString(), anyString(), anyString()))
                .thenReturn(-1L);

        RedisStockReservationStore store = new RedisStockReservationStore(redisTemplate, "async");

        assertThat(store.reserveRedisOnly(5L, 100L, 3)).isEqualTo(-1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void reserveRedisOnlyDegradesToNotPreloadedWhenScriptReturnsNull() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.<Long>execute(any(RedisScript.class), any(List.class),
                anyString(), anyString(), anyString()))
                .thenReturn(null);

        RedisStockReservationStore store = new RedisStockReservationStore(redisTemplate, "async");

        assertThat(store.reserveRedisOnly(5L, 100L, 3)).isEqualTo(-1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void preloadAvailableSetsTheAvailableKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        RedisStockReservationStore store = new RedisStockReservationStore(redisTemplate, "async");

        store.preloadAvailable(5L, 42L);

        verify(valueOps).set(RedisReservationKeys.availableKey(5L), "42");
    }
}
