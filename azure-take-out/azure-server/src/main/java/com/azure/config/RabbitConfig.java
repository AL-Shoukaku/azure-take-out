package com.azure.config;

import com.azure.mq.RabbitConstants;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.Queue;

@Configuration
public class RabbitConfig {

    /**
     * 创建优惠券订单创建的队列
     */
    @Bean
    public Queue couponOrderCreateQueue() {
        return new Queue(RabbitConstants.COUPON_ORDER_CREATE_QUEUE);
    }

    /**
     * 设置消息转换器
     */
    @Bean
    public Jackson2JsonMessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
