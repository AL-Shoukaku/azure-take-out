package com.azure.service.impl;

import com.azure.cache.CacheClient;
import com.azure.cache.RedisConstants;
import com.azure.json.JacksonObjectMapper;
import com.azure.mapper.CouponMapper;
import com.azure.support.ServiceTestSupport;
import com.azure.vo.coupon.AvailableCouponVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实CacheClient和JSON序列化，验证列表缓存命中、回源及空列表。 */
class CouponListCacheTest extends ServiceTestSupport {
    @InjectMocks private CouponServiceImpl service;
    @Mock private CouponMapper mapper;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    @Mock private RedissonClient redisson;
    @Mock private RLock lock;
    private final JacksonObjectMapper json = new JacksonObjectMapper();

    @BeforeEach
    void injectCacheClient() {
        CacheClient cache = new CacheClient();
        ReflectionTestUtils.setField(cache, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(cache, "objectMapper", json);
        ReflectionTestUtils.setField(cache, "redissonClient", redisson);
        ReflectionTestUtils.setField(service, "cacheClient", cache);
        when(redis.opsForValue()).thenReturn(values);
    }

    private List<AvailableCouponVO> coupons(boolean empty) {
        if (empty) return List.of();
        AvailableCouponVO coupon = new AvailableCouponVO();
        coupon.setId(7L);
        coupon.setName("午餐代金券");
        coupon.setType(1);
        coupon.setStock(20);
        coupon.setPrice(new BigDecimal("9.90"));
        coupon.setDiscountAmount(new BigDecimal("20.00"));
        coupon.setThresholdAmount(BigDecimal.ZERO);
        coupon.setStartTime(LocalDateTime.of(2026, 11, 1, 10, 0));
        coupon.setEndTime(LocalDateTime.of(2026, 11, 30, 10, 0));
        return List.of(coupon);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cachedListReturnsSnapshotWithoutQueryingDatabase(boolean empty) throws Exception {
        List<AvailableCouponVO> expected = coupons(empty);
        when(values.get(RedisConstants.COUPON_LIST_KEY)).thenReturn(json.writeValueAsString(expected));

        assertThat(service.queryAvailableCoupons()).usingRecursiveComparison().isEqualTo(expected);

        verifyNoInteractions(mapper, redisson);
        verify(values, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingListLoadsAndCachesJsonWithShortExpiry(boolean empty) throws Exception {
        List<AvailableCouponVO> expected = coupons(empty);
        when(redisson.getLock(RedisConstants.LOCK_KEY_PREFIX + RedisConstants.COUPON_LIST_KEY)).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(mapper.getAvailableCoupons()).thenReturn(expected);

        assertThat(service.queryAvailableCoupons()).isEqualTo(expected);

        verify(mapper).getAvailableCoupons();
        verify(values).set(eq(RedisConstants.COUPON_LIST_KEY), eq(json.writeValueAsString(expected)),
                longThat(ttl -> ttl >= RedisConstants.CACHE_COUPON_LIST_TTL * 0.9
                        && ttl <= RedisConstants.CACHE_COUPON_LIST_TTL * 1.1), eq(TimeUnit.SECONDS));
        verify(lock).unlock();
    }
}
