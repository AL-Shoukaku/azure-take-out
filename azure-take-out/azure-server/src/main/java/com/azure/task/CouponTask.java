package com.azure.task;

import com.azure.cache.CacheClient;
import com.azure.cache.RedisConstants;
import com.azure.entity.CouponOrder;
import com.azure.mapper.CouponMapper;
import com.azure.mapper.CouponOrderMapper;
import com.azure.mapper.UserCouponMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class CouponTask {

    @Autowired
    private CouponOrderMapper couponOrderMapper;

    @Autowired
    private CouponMapper couponMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Autowired
    private CacheClient cacheClient;

    /**
     * 处理超时代金券订单
     */
    @Scheduled(cron = "0 * * * * *") // 每分钟执行一次
    @Transactional
    public void processTimeOutCouponOrders() {
        LocalDateTime now = LocalDateTime.now();
        log.info("处理超时代金券订单:{}", now);
        List<CouponOrder> orders = couponOrderMapper.getExpiredUnpaidOrders(now);
        for (CouponOrder order : orders) {
            if (couponOrderMapper.closeExpiredUnpaidOrder(order.getId(), now) == 0) {
                continue;
            }
            couponMapper.addStockByOne(order.getCouponId());
            cacheClient.incrStockAfterCommit(RedisConstants.COUPON_STOCK_KEY_PREFIX + order.getCouponId());
        }
    }

    /**
     * 处理过期的用户持有的优惠券
     */
    @Scheduled(cron = "0 * * * * *") // 每分钟执行一次
    public void processExpiredUserCoupons() {
        log.info("处理过期的用户持有的优惠券:{}", LocalDateTime.now());
        userCouponMapper.updateExpiredUserCouponsToExpired();
    }
}
