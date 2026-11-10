package com.azure.cache;

import com.azure.exception.CacheBusinessException;
import com.azure.handler.GlobalExceptionHandler;
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
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @RestController
    static class CacheTestController {

        private final CacheClient cacheClient;

        CacheTestController(CacheClient cacheClient) {
            this.cacheClient = cacheClient;
        }

        @GetMapping("/cache-test")
        public Dish getDish() {
            return cacheClient.getOrLoad(KEY, new TypeReference<Dish>() {}, () -> DISH, 300);
        }
    }

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
    void getOrLoadWrapsDeserializationFailureInBusinessException() throws JsonProcessingException {
        JsonProcessingException cause = mock(JsonProcessingException.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY)).thenReturn("not-json");
        when(objectMapper.readValue("not-json", dishType)).thenThrow(cause);

        assertThatThrownBy(() -> cacheClient.getOrLoad(KEY, dishType, loaderShouldNotRun(), 300))
                .isInstanceOf(CacheBusinessException.class)
                .hasMessageContaining("反序列化")
                .hasCause(cause);
    }

    @Test
    void setWrapsSerializationFailureInBusinessException() throws JsonProcessingException {
        JsonProcessingException cause = mock(JsonProcessingException.class);
        when(objectMapper.writeValueAsString(any())).thenThrow(cause);

        assertThatThrownBy(() -> cacheClient.set(KEY, DISH, 300))
                .isInstanceOf(CacheBusinessException.class)
                .hasMessageContaining("序列化")
                .hasCause(cause);
    }

    @Test
    void getOrLoadThrowsBusinessExceptionWhenLockRetriesAreExhausted() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redissonClient.getLock("lock:" + KEY)).thenReturn(lock);
        when(lock.tryLock()).thenReturn(false);

        assertThatThrownBy(() -> cacheClient.getOrLoad(KEY, dishType, loaderShouldNotRun(), 300))
                .isInstanceOf(CacheBusinessException.class)
                .hasMessage("无法获取缓存锁！");

        verify(lock, times(1 + RedisConstants.LOCK_MAX_RETRY_COUNT)).tryLock();
        verify(lock, never()).unlock();
    }

    @Test
    void getOrLoadPreservesInterruptWhenThrowingBusinessException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redissonClient.getLock("lock:" + KEY)).thenReturn(lock);
        when(lock.tryLock()).thenReturn(false);

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> cacheClient.getOrLoad(KEY, dishType, loaderShouldNotRun(), 300))
                    .isInstanceOf(CacheBusinessException.class)
                    .hasMessage("缓存加载被中断")
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(lock, never()).unlock();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void cacheFailureReturnsUnifiedBusinessErrorThroughGlobalHandler() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redissonClient.getLock("lock:" + KEY)).thenReturn(lock);
        when(lock.tryLock()).thenReturn(false);

        MockMvcBuilders.standaloneSetup(new CacheTestController(cacheClient))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/cache-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("无法获取缓存锁！"));
    }

    @Test
    void deleteByKeyDeletesSingleKey() {
        cacheClient.deleteByKey(KEY);

        verify(redisTemplate).delete(KEY);
    }

    @Test
    void stockRestoreRunsOnceWithoutTransaction() {
        String stockKey = RedisConstants.COUPON_STOCK_KEY_PREFIX + 7;

        cacheClient.incrStockAfterCommit(stockKey);

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(stockKey)));
        verifyNoMoreInteractions(redisTemplate);
    }

    @Test
    void stockRestoreWaitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            cacheClient.incrStockAfterCommit(KEY);
            verifyNoInteractions(redisTemplate);

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(KEY)));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void stockRestoreIsSkippedOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            cacheClient.incrStockAfterCommit(KEY);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verifyNoInteractions(redisTemplate);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void stockRestoreFailureDoesNotRetryOrPreventOtherCommitCallbacks() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(KEY))))
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            cacheClient.incrStockAfterCommit(KEY);
            cacheClient.incrStockAfterCommit(KEY + ":other");

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(KEY)));
            verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(KEY + ":other")));
            verifyNoMoreInteractions(redisTemplate);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
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
