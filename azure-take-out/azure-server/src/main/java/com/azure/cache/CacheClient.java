package com.azure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class CacheClient {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedissonClient redissonClient;

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

        // 如果未获取到锁，说明其他线程正在加载数据，当前线程可以选择等待一段时间后重试
        if (!lockAcquired) {
            try {
                Thread.sleep(50); // 等待 50 毫秒后重试
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("缓存加载被中断", e);
            }
            return getOrLoad(key, type, loader, ttl); // 递归重试
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
     * 将对象序列化为 JSON 字符串，封装 try-catch
     * @param object    要序列化的对象
     * @return          序列化后的 JSON 字符串
     */
    private String serialize(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("无法将对象序列化为JSON！", e);
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
            throw new RuntimeException("无法将JSON反序列化为对象！", e);
        }
    }
}
