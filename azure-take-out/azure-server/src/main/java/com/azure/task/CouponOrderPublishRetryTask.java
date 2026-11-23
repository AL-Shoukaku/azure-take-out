package com.azure.task;

import com.azure.cache.CacheClient;
import com.azure.entity.CouponOrderPublishRetry;
import com.azure.exception.CacheBusinessException;
import com.azure.mapper.CouponOrderPublishRetryMapper;
import com.azure.message.CouponOrderCreateMessage;
import com.azure.mq.coupon.CouponOrderCreateProducer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponOrderPublishRetryTask {

    private static final int BATCH_SIZE = 5;

    private final CouponOrderPublishRetryMapper retryMapper;
    private final CouponOrderCreateProducer producer;
    private final CacheClient cacheClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(cron = "*/30 * * * * *")
    public void retryPendingMessages() {
        for (CouponOrderPublishRetry record : retryMapper.selectPending(BATCH_SIZE)) {
            try {
                CouponOrderCreateMessage message =
                        objectMapper.readValue(record.getMessage(), CouponOrderCreateMessage.class);
                transactionTemplate.executeWithoutResult(status -> {
                    boolean sent = producer.sendMessage(record.getUserId(), message.getCoupon(),
                            message.getCouponNumber(), record.getOrderNumber());
                    if (!sent) {
                        restoreReservation(record);
                    }
                    // 发送结果仍未知时，sendMessage 已插入新记录。新记录与旧记录的删除一起提交。
                    if (retryMapper.deleteById(record.getId()) == 0) {
                        status.setRollbackOnly();
                    }
                });
            } catch (JsonProcessingException | RuntimeException e) {
                log.error("处理购券消息补发失败，记录ID={}，订单号={}", record.getId(), record.getOrderNumber(), e);
            }
        }
    }

    private void restoreReservation(CouponOrderPublishRetry record) {
        Long result = cacheClient.restoreCouponReservation(record.getCouponId(), record.getUserId(),
                record.getOrderNumber());
        if (result != null && result == -3L) {
            log.warn("购券补偿未恢复库存，缓存缺失或用户标记异常，订单号={}", record.getOrderNumber());
        } else if (result == null || (result != 0L && result != 1L)) {
            throw new CacheBusinessException("恢复购券预扣失败，订单号=" + record.getOrderNumber() + "，结果=" + result);
        }
    }
}
