package com.azure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RKeys;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CacheClientTest {

    private static final String KEY = "cache:dish:1";
    private static final String DISH_JSON = "{\"id\":1,\"name\":\"宫保鸡丁\"}";
    private static final Dish DISH = new Dish(1L, "宫保鸡丁");

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final RKeys keys = mock(RKeys.class);

    private final CacheClient cacheClient = new CacheClient();
    private final TypeReference<Dish> dishType = new TypeReference<>() {};

    record Dish(Long id, String name) {}

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cacheClient, "stringRedisTemplate", redisTemplate);
        ReflectionTestUtils.setField(cacheClient, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(cacheClient, "redissonClient", redissonClient);
    }

    /** 不应该被执行的 loader，一旦执行直接让测试失败 */
    private Supplier<Dish> loaderShouldNotRun() {
        return () -> {
            throw new AssertionError("loader 不应被执行");
        };
    }

    @Test
    void setWithExpirationJittersWithinTenPercent() throws JsonProcessingException {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(objectMapper.writeValueAsString(any())).thenReturn(DISH_JSON);

        for (int i = 0; i < 100; i++) {
            cacheClient.set(KEY, DISH, 300);
        }

        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        verify(valueOps, times(100)).set(eq(KEY), eq(DISH_JSON), ttl.capture(), eq(TimeUnit.SECONDS));
        assertThat(ttl.getAllValues()).allMatch(v -> v >= 270 && v < 330);
    }

    @Test
    void getOrLoadReturnsCachedObjectWithoutCallingLoader() throws JsonProcessingException {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY)).thenReturn(DISH_JSON);
        when(objectMapper.readValue(DISH_JSON, dishType)).thenReturn(DISH);

        Dish result = cacheClient.getOrLoad(KEY, dishType, loaderShouldNotRun(), 300);

        assertThat(result).isEqualTo(DISH);
        verifyNoInteractions(redissonClient);
    }

    @Test
    void getOrLoadTreatsEmptyStringValueAsNullSentinel() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY)).thenReturn("");

        Dish result = cacheClient.getOrLoad(KEY, dishType, loaderShouldNotRun(), 300);

        assertThat(result).isNull();
        verifyNoInteractions(redissonClient);
    }

    @Test
    void getOrLoadLoadsFromLoaderAndCachesOnMiss() throws JsonProcessingException {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY)).thenReturn(null);
        when(redissonClient.getLock("lock:" + KEY)).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(objectMapper.writeValueAsString(DISH)).thenReturn(DISH_JSON);

        Dish result = cacheClient.getOrLoad(KEY, dishType, () -> DISH, 300);

        assertThat(result).isEqualTo(DISH);
        verify(valueOps).set(eq(KEY), eq(DISH_JSON), anyLong(), eq(TimeUnit.SECONDS));
        verify(lock).unlock();
    }

    @Test
    void getOrLoadCachesEmptySentinelWhenLoaderReturnsNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY)).thenReturn(null);
        when(redissonClient.getLock("lock:" + KEY)).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);

        Dish result = cacheClient.getOrLoad(KEY, dishType, () -> null, 300);

        assertThat(result).isNull();
        verify(valueOps).set(KEY, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
        verify(lock).unlock();
    }

    @Test
    void getOrLoadSkipsLoadingWhenDoubleCheckFindsCache() throws JsonProcessingException {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // 第一次 get 未命中，拿到锁后 double check 命中
        when(valueOps.get(KEY)).thenReturn(null, DISH_JSON);
        when(redissonClient.getLock("lock:" + KEY)).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(objectMapper.readValue(DISH_JSON, dishType)).thenReturn(DISH);

        Dish result = cacheClient.getOrLoad(KEY, dishType, loaderShouldNotRun(), 300);

        assertThat(result).isEqualTo(DISH);
        verify(lock).unlock();
    }

    @Test
    void getOrLoadRetriesUntilCacheIsFilledWhenLockIsBusy() throws JsonProcessingException {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // 第一次 get 未命中且抢锁失败，重试时缓存已被其他线程写入
        when(valueOps.get(KEY)).thenReturn(null, DISH_JSON);
        when(redissonClient.getLock("lock:" + KEY)).thenReturn(lock);
        when(lock.tryLock()).thenReturn(false);
        when(objectMapper.readValue(DISH_JSON, dishType)).thenReturn(DISH);

        Dish result = cacheClient.getOrLoad(KEY, dishType, loaderShouldNotRun(), 300);

        assertThat(result).isEqualTo(DISH);
        verify(lock, times(1)).tryLock();
        verify(lock, never()).unlock();
    }

    @Test
    void getOrLoadWrapsDeserializationFailureInRuntimeError() throws JsonProcessingException {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY)).thenReturn("not-json");
        when(objectMapper.readValue("not-json", dishType)).thenThrow(mock(JsonProcessingException.class));

        assertThatThrownBy(() -> cacheClient.getOrLoad(KEY, dishType, loaderShouldNotRun(), 300))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("反序列化");
    }

    @Test
    void setWrapsSerializationFailureInRuntimeError() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenThrow(mock(JsonProcessingException.class));

        assertThatThrownBy(() -> cacheClient.set(KEY, DISH, 300))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("序列化");
    }

    @Test
    void deleteByKeyDeletesSingleKey() {
        cacheClient.deleteByKey(KEY);

        verify(redisTemplate).delete(KEY);
    }

    @Test
    void deleteByKeyAfterCommitDeletesImmediatelyWithoutTransaction() {
        cacheClient.deleteByKeyAfterCommit(KEY);

        verify(redisTemplate).delete(KEY);
    }

    @Test
    void deleteByPrefixAfterCommitDeletesImmediatelyWithoutTransaction() {
        when(redissonClient.getKeys()).thenReturn(keys);

        cacheClient.deleteByPrefixAfterCommit("cache:dish:");

        verify(keys).deleteByPattern("cache:dish:*");
    }

    @Test
    void deleteByKeyAfterCommitDefersUntilCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            cacheClient.deleteByKeyAfterCommit(KEY);

            verify(redisTemplate, never()).delete(KEY); // 提交前不删除
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(redisTemplate).delete(KEY); // 提交后删除
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteByKeyAfterCommitDoesNothingWhenTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            cacheClient.deleteByKeyAfterCommit(KEY);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(redisTemplate, never()).delete(KEY); // 回滚不删除
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteByPrefixAppendsGlobStarToPrefix() {
        when(redissonClient.getKeys()).thenReturn(keys);

        cacheClient.deleteByPrefix("cache:dish:");

        verify(keys).deleteByPattern("cache:dish:*");
    }
}
