package com.azure.mq.coupon;

import com.azure.entity.Coupon;
import com.azure.entity.CouponOrderPublishRetry;
import com.azure.mapper.CouponOrderPublishRetryMapper;
import com.azure.message.CouponOrderCreateMessage;
import com.azure.mq.RabbitConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class CouponOrderCreateProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CouponOrderPublishRetryMapper couponOrderPublishRetryMapper;

    /**
     * 发送购券订单创建的消息到消息队列
     * @param userId        用户ID
     * @param coupon        优惠券模板
     * @param couponNumber  优惠券编号
     * @param orderNumber   订单号
     * @return              发布确认成功或结果未知但已保存补发记录时返回 true，明确失败时返回 false
     */
    public boolean sendMessage(Long userId, Coupon coupon, String couponNumber, String orderNumber) {
        CouponOrderCreateMessage couponOrderCreateMessage = new CouponOrderCreateMessage();
        couponOrderCreateMessage.setCoupon(coupon);
        couponOrderCreateMessage.setUserId(userId);
        couponOrderCreateMessage.setCouponNumber(couponNumber);
        couponOrderCreateMessage.setOrderNumber(orderNumber);
        CorrelationData correlationData = new CorrelationData();
        CorrelationData.Confirm confirm;
        try {
            rabbitTemplate.convertAndSend(RabbitConstants.COUPON_ORDER_CREATE_QUEUE,
                    couponOrderCreateMessage, correlationData);
            confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);
        } catch (MessageConversionException e) {
            log.error("购券消息转换失败，订单号={}", orderNumber, e);
            return false;
        } catch (AmqpException | InterruptedException | ExecutionException | TimeoutException e) {
            try {
                if (correlationData.getReturned() != null) {
                    return false;
                }
                log.warn("购券消息发布结果未知，订单号={}", orderNumber, e);
                saveRetryMessage(couponOrderCreateMessage);
                return true;
            } finally {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (correlationData.getReturned() != null) {
            return false;
        }
        // Spring AMQP 会在通道异常关闭时生成带原因的 NACK，此时投递结果未知。
        if (confirm.isAck() || confirm.getReason() == null) {
            return confirm.isAck();
        }
        log.warn("购券消息发布结果未知，订单号={}，确认原因={}", orderNumber, confirm.getReason());
        saveRetryMessage(couponOrderCreateMessage);
        return true;
    }

    private void saveRetryMessage(CouponOrderCreateMessage message) {
        Message amqpMessage = rabbitTemplate.getMessageConverter().toMessage(message, new MessageProperties());
        CouponOrderPublishRetry retry = new CouponOrderPublishRetry();
        retry.setCouponId(message.getCoupon().getId());
        retry.setUserId(message.getUserId());
        retry.setOrderNumber(message.getOrderNumber());
        retry.setMessage(new String(amqpMessage.getBody(), StandardCharsets.UTF_8));
        couponOrderPublishRetryMapper.insertCouponOrderPublishRetry(retry);
    }
}
