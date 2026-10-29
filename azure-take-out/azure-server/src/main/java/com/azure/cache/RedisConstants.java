package com.azure.cache;

public class RedisConstants {
    // 缓存 key 前缀
    public static final String SHOP_STATUS_KEY = "shop:status";         // 店铺状态只会有一个，这个不算前缀
    public static final String DISH_KEY_PREFIX = "dish:";               // 菜品 key 前缀
    public static final String SETMEAL_KEY_PREFIX = "setmeal:";         // 套餐 key 前缀
    public static final String SETMEALDISH_KEY_PREFIX = "setmealDish:"; // 套餐内订单 key 前缀
    public static final String CATEGORY_KEY_PREFIX = "category:";       // 分类 key 前缀

    // 锁 key 前缀
    public static final String LOCK_KEY_PREFIX = "lock:";               // 锁 key 前缀

    // 过期时间,单位：秒
    public static final long CACHE_NULL_TTL = 60;                   // 无效数据的过期时间
    public static final long CACHE_SHOP_STATUS_TTL = 30 * 60;              // 店铺缓存过期时间
    public static final long CACHE_DISH_TTL = 30 * 60;                     // 菜品缓存过期时间
    public static final long CACHE_SETMEAL_TTL = 30 * 60;           // 套餐缓存过期时间
    public static final long CACHE_SETMEALDISH_TTL = 30 * 60;     // 套餐内订单缓存过期时间
    public static final long CACHE_CATEGORY_TTL = 30 * 60;         // 分类缓存过期时间
}
