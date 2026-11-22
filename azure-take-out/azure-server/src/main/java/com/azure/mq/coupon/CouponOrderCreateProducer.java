package com.azure.mq.coupon;

import com.azure.entity.Coupon;
import com.azure.exception.BaseException;
import com.azure.message.CouponOrderCreateMessage;
import com.azure.mq.RabbitConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class CouponOrderCreateProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送购券订单创建的消息到消息队列
     * @param userId        用户ID
     * @param coupon        优惠券模板
     * @param couponNumber  优惠券编号
     * @param orderNumber   订单号
     * @return              明确收到 Return 时返回 false，其余情况按订单号继续查单
     */
    public boolean sendMessage(Long userId, Coupon coupon, String couponNumber, String orderNumber) {
        CouponOrderCreateMessage couponOrderCreateMessage = new CouponOrderCreateMessage();
        couponOrderCreateMessage.setCoupon(coupon);
        couponOrderCreateMessage.setUserId(userId);
        couponOrderCreateMessage.setCouponNumber(couponNumber);
        couponOrderCreateMessage.setOrderNumber(orderNumber);
        CorrelationData correlationData = new CorrelationData();    // 用于接收队列的返回信息
        rabbitTemplate.convertAndSend(RabbitConstants.COUPON_ORDER_CREATE_QUEUE,
                couponOrderCreateMessage,
                correlationData
        );
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);
            if (correlationData.getReturned() != null) {
                return false;
            }
            if (!confirm.isAck()) {
                log.warn("优惠券订单发布结果未知，订单号={}，确认原因={}", orderNumber, confirm.getReason());
            }
            return true;
        } catch (InterruptedException | ExecutionException | TimeoutException  e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (correlationData.getReturned() != null)
                return false;
            log.warn("优惠券订单信息发送超时，订单号={}", orderNumber, e);
            return true;
        }
    }
}
