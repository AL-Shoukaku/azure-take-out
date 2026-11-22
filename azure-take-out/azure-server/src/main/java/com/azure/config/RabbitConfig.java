package com.azure.config;

import com.azure.mq.RabbitConstants;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecovererWithConfirms;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    /**
     * 创建优惠券订单创建的队列
     */
    @Bean
    public Queue couponOrderCreateQueue() {
        return new Queue(RabbitConstants.COUPON_ORDER_CREATE_QUEUE);
    }

    @Bean
    public Declarables couponOrderCreateDeadLetterTopology() {
        DirectExchange exchange = new DirectExchange(RabbitConstants.COUPON_ORDER_CREATE_DLX);
        Queue queue = new Queue(RabbitConstants.COUPON_ORDER_CREATE_DLQ);
        return new Declarables(exchange, queue, BindingBuilder.bind(queue)
                .to(exchange)
                .with(RabbitConstants.COUPON_ORDER_CREATE_FAILED_ROUTING_KEY));
    }

    @Bean
    public SimpleRabbitListenerContainerFactory couponOrderCreateListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            RabbitTemplate rabbitTemplate) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);

        RepublishMessageRecovererWithConfirms recoverer = new RepublishMessageRecovererWithConfirms(
                rabbitTemplate,
                RabbitConstants.COUPON_ORDER_CREATE_DLX,
                RabbitConstants.COUPON_ORDER_CREATE_FAILED_ROUTING_KEY,
                CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000, 1.0, 1000)
                .recoverer(recoverer)
                .build());
        factory.setDefaultRequeueRejected(true);
        return factory;
    }

    /**
     * 设置消息转换器
     */
    @Bean
    public Jackson2JsonMessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
