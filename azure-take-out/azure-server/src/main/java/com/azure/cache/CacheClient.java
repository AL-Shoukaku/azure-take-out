package com.azure.cache;

import com.azure.entity.Coupon;
import com.azure.exception.CacheBusinessException;
import com.azure.mapper.CouponMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static jodd.util.ThreadUtil.sleep;

@Component
@Slf4j
public class CacheClient {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private CouponMapper couponMapper;

    private final static DefaultRedisScript<Long> SADD_SCRIPT;

    // 初始化 lua 脚本
    static {
        SADD_SCRIPT = new DefaultRedisScript<>();
        SADD_SCRIPT.setLocation(new ClassPathResource("lua/sadd.lua"));
        SADD_SCRIPT.setResultType(Long.class);
    }


    /**
     * 设置带有过期时间的缓存，时间单位为秒，带有 10% 的上下浮动，避免缓存雪崩
     * @param key            缓存的键
     * @param object         缓存的对象
     * @param time           缓存的过期时间，单位为秒
     */
    public void set(String key, Object object, long time) {
        String value = serialize(object);
        long maxTime = (long) (time * 1.1); // 上浮 10%
        long minTime = (long) (time * 0.9); // 下浮 10%
        long ttl = (long) (Math.random() * (maxTime - minTime) + minTime); // 随机过期时间
        stringRedisTemplate.opsForValue().set(key, value, ttl, TimeUnit.SECONDS);
    }

    /**
     * 设置带有过期时间的 Set 缓存，时间单位为秒，带有 10% 的上下浮动，避免缓存雪崩
     * @param key            缓存的键
     * @param value          缓存的值
     * @param time           缓存的过期时间，单位为秒
     */
    public void sadd(String key, String value, long time) {
        long maxTime = (long) (time * 1.1); // 上浮 10%
        long minTime = (long) (time * 0.9); // 下浮 10%
        long ttl = (long) (Math.random() * (maxTime - minTime) + minTime); // 随机过期时间
        stringRedisTemplate.execute(SADD_SCRIPT, Collections.singletonList(key), value, String.valueOf(ttl));
    }

    /**
     * 从 Redis 中根据 key 获取对象，如果不存在则使用 loader 加载数据并缓存，缓存时间为 ttl 秒
     * 缓存更新时使用分布式互斥锁，避免缓存击穿
     * @param key       缓存的键
     * @param type      对象的类型
     * @param loader    加载数据的函数
     * @param ttl       缓存的过期时间，单位为秒
     * @return          缓存的对象
     * @param <T>       对象的类型
     */
    public <T> T getOrLoad(String key, TypeReference<T> type, Supplier<T> loader, long ttl) {
        String value = stringRedisTemplate.opsForValue().get(key);
        // 缓存命中时，如果是空串就返回 null，否则反序列化为对象并返回
        if (value != null) {
            return (value.isEmpty()) ? null : deSerialize(value, type);
        }

        // 缓存未命中，尝试获取互斥锁，获取后才能加载数据并缓存，防止缓存击穿
        String lockKey = "lock:" + key;
        RLock lock = redissonClient.getLock(lockKey);
        boolean lockAcquired = lock.tryLock();

        // 获取不到锁，最多重试 5 次，每次等待 50 毫秒，检查缓存仍未命中时重试
        for (int retry = 0; retry < RedisConstants.LOCK_MAX_RETRY_COUNT && !lockAcquired; retry++) {

            try {
                Thread.sleep(50); // 等待 50 毫秒后重试
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CacheBusinessException("缓存加载被中断", e);
            }
            value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                return (value.isEmpty()) ? null : deSerialize(value, type);
            }
            lockAcquired = lock.tryLock();
        }
        if (!lockAcquired) {
            throw new CacheBusinessException("无法获取缓存锁！");
        }

        // 获取到锁后，先加载并缓存数据，最后释放锁
        T result = null;
        try {
            // 再次检查缓存是否已经被其他线程加载了数据，避免重复加载
            value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                return (value.isEmpty()) ? null : deSerialize(value, type);
            }
            // 加载数据，如果为 null，则缓存空串，避免缓存穿透，过期时间为无效数据的统一 TTL
            result = loader.get();
            if (result == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
            } else {
                set(key, result, ttl);
            }
        } finally {
            // 释放锁，double check 或加载数据抛异常时也不能泄漏锁
            lock.unlock();
        }
        return result;
    }

    /**
     * 删除指定 key 的缓存
     * @param key         缓存的键
     */
    public void deleteByKey(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 根据指定前缀删除缓存
     * @param prefix     批量删除缓存的前缀
     */
    public void deleteByPrefix(String prefix) {
         redissonClient.getKeys().deleteByPattern(prefix + "*");
    }

    /**
     * 在事务提交后删除指定 key 的缓存。
     * 调用方处于事务中时，删除被注册到事务提交之后执行——若在提交前删除，
     * 并发读会用未提交的旧数据回填缓存，导致缓存脏到 TTL；事务回滚时自动跳过删除。
     * 无事务上下文时立即删除。
     * @param key        缓存的键
     */
    public void deleteByKeyAfterCommit(String key) {
        runAfterCommit(key, () -> deleteByKey(key));
    }

    /**
     * 在事务提交后按前缀批量删除缓存，事务语义同 {@link #deleteByKeyAfterCommit(String)}。
     * @param prefix     批量删除缓存的前缀
     */
    public void deleteByPrefixAfterCommit(String prefix) {
        runAfterCommit(prefix + "*", () -> deleteByPrefix(prefix));
    }

    /**
     * 专门用于查询单张优惠券信息的方法。
     * 未命中时，会根据优惠券的活动结束时间计算过期时间。
     * @param couponId  优惠券ID
     * @return          优惠券信息
     */
    public Coupon getOrLoadCoupon(Long couponId) {
        String key = RedisConstants.COUPON_INFO_KEY_PREFIX + couponId;
        TypeReference<Coupon> type = new TypeReference<>() {};
        Supplier<Coupon> loader = () -> couponMapper.selectById(couponId);
        String value = stringRedisTemplate.opsForValue().get(key);
        // 缓存命中时，如果是空串就返回 null，否则反序列化为对象并返回
        if (value != null) {
            return (value.isEmpty()) ? null : deSerialize(value, type);
        }

        // 缓存未命中，尝试获取互斥锁，获取后才能加载数据并缓存，防止缓存击穿
        String lockKey = "lock:" + key;
        RLock lock = redissonClient.getLock(lockKey);
        boolean lockAcquired = lock.tryLock();

        // 获取不到锁，最多重试 5 次，每次等待 50 毫秒，检查缓存仍未命中时重试
        for (int retry = 0; retry < RedisConstants.LOCK_MAX_RETRY_COUNT && !lockAcquired; retry++) {

            try {
                Thread.sleep(50); // 等待 50 毫秒后重试
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CacheBusinessException("缓存加载被中断", e);
            }
            value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                return (value.isEmpty()) ? null : deSerialize(value, type);
            }
            lockAcquired = lock.tryLock();
        }
        if (!lockAcquired) {
            throw new CacheBusinessException("无法获取缓存锁！");
        }

        // 获取到锁后，先加载并缓存数据，最后释放锁
        Coupon result = null;
        try {
            // 再次检查缓存是否已经被其他线程加载了数据，避免重复加载
            value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                return (value.isEmpty()) ? null : deSerialize(value, type);
            }
            // 加载数据，如果为 null，则缓存空串，避免缓存穿透，过期时间为无效数据的统一 TTL
            result = loader.get();
            if (result == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
            } else {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime endTime = result.getEndTime();
                long ttl = (long) (Duration.between(now, endTime).getSeconds() * 1.2); // 上浮 20%，确保在活动结束以后过期
                ttl = Math.max(ttl, RedisConstants.CACHE_COUPON_INFO_MIN_TTL); // 最短过期时间为 60 秒，防止过短或活动已经过期
                set(key, result, ttl);
            }
        } finally {
            // 释放锁，double check 或加载数据抛异常时也不能泄漏锁
            lock.unlock();
        }
        return result;
    }

    /** 有活跃事务时把删除动作挂到 afterCommit，否则立即执行 */
    private void runAfterCommit(String target, Runnable delete) {
        Runnable deleteWithRetry = () -> deleteWithRetry(target, delete);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteWithRetry.run();
                }
            });
        } else {
            deleteWithRetry.run();
        }
    }

    /**
     * 调用删除缓存的 Runnable，若抛出异常则重试，超过重试次数则记录异常日志
     * @param target    要删除缓存的目标，日志中使用
     * @param delete    Runnable 删除缓存的操作
     */
    private void deleteWithRetry(String target, Runnable delete) {
        for (int retry = 1; retry <= RedisConstants.DELETE_CACHE_MAX_ATTEMPTS; retry++) {
            try {
                delete.run();
                return;
            } catch(DataAccessException | RedisException e) {
                log.warn("删除缓存失败，重试中...，目标：{}，重试次数：{}", target, retry, e);

                if (retry == RedisConstants.DELETE_CACHE_MAX_ATTEMPTS) {
                    log.error("删除缓存失败，超过最大重试次数，目标：{}", target, e);
                    return;
                }

                int waitTime = (retry) * 100;
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    log.error("删除缓存重试等待被中断，目标：{}，重试次数：{}", target, retry, ex);
                    return;
                }
            }
        }
    }

    /**
     * 将对象序列化为 JSON 字符串，封装 try-catch
     * @param object    要序列化的对象
     * @return          序列化后的 JSON 字符串
     */
    private String serialize(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new CacheBusinessException("无法将对象序列化为JSON！", e);
        }
    }

    /**
     * 根据指定的 JSON 字符串反序列化为对象，封装 try-catch
     * @param value     JSON 字符串
     * @param type      反序列化后的对象类型
     * @return          反序列化后的对象
     * @param <T>       对象的类型
     */
    private <T> T deSerialize(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new CacheBusinessException("无法将JSON反序列化为对象！", e);
        }
    }
}
