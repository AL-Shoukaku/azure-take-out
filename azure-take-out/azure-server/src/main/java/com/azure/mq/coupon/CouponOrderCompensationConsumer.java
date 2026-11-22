package com.azure.mq.coupon;

import com.azure.cache.CacheClient;
import com.azure.entity.Coupon;
import com.azure.exception.CacheBusinessException;
import com.azure.message.CouponOrderCreateMessage;
import com.azure.mq.RabbitConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CouponOrderCompensationConsumer {

    @Autowired
    private CacheClient cacheClient;

    @RabbitListener(queues = RabbitConstants.COUPON_ORDER_CREATE_DLQ)
    public void compensateCouponOrder(CouponOrderCreateMessage message) {
        Coupon coupon = message.getCoupon();
        Long userId = message.getUserId();
        String orderNumber = message.getOrderNumber();
        if (coupon == null || coupon.getId() == null || userId == null
                || orderNumber == null || orderNumber.isBlank() || "-1".equals(orderNumber)) {
            throw new IllegalArgumentException("异常的购券补偿消息，订单号=" + orderNumber);
        }

        Long result = cacheClient.restoreCouponReservation(coupon.getId(), userId, orderNumber);
        if (result != null && result == -3L) {
            log.warn("购券补偿未恢复库存，缓存缺失或用户标记异常，券ID={}，用户ID={}，订单号={}",
                    coupon.getId(), userId, orderNumber);
            return;
        }
        if (result == null || (result != 0L && result != 1L)) {
            throw new CacheBusinessException("恢复购券预扣失败，订单号=" + orderNumber + "，结果=" + result);
        }
    }
}
