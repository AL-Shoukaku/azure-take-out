package com.azure.task;

import com.azure.mapper.CouponOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class CouponTask {
    @Autowired
    private CouponOrderMapper couponOrderMapper;
    @Scheduled(cron = "0 * * * * *") // 每分钟执行一次
    public void processTimeOutCouponOrders() {
        log.info("处理超时代金券订单:{}", LocalDateTime.now());
        couponOrderMapper.updateExpireAndUnpaidOrdersToClosed();
    }
}
