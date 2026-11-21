package com.azure.mq.coupon;

import com.azure.entity.Coupon;
import com.azure.message.CouponOrderCreateMessage;
import com.azure.mq.RabbitConstants;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CouponOrderCreateProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendMessage(Long userId, Coupon coupon, String couponNumber, String orderNumber) {
        CouponOrderCreateMessage couponOrderCreateMessage = new CouponOrderCreateMessage();
        couponOrderCreateMessage.setCoupon(coupon);
        couponOrderCreateMessage.setUserId(userId);
        couponOrderCreateMessage.setCouponNumber(couponNumber);
        couponOrderCreateMessage.setOrderNumber(orderNumber);
        rabbitTemplate.convertAndSend(RabbitConstants.COUPON_ORDER_CREATE_QUEUE, couponOrderCreateMessage);
    }
}
