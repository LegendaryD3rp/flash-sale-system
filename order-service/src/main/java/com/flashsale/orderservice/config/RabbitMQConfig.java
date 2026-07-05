package com.flashsale.orderservice.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 队列配置 — 秒杀落单（订单服务侧）
 *
 * 与 seckill-service 的 RabbitMQConfig 声明相同的 exchange/queue，
 * 但此处仅声明队列绑定，不重新创建（由 seckill-service 创建，此处可共用，
 * 但为了解耦，各自声明同名队列，RabbitMQ 自动幂等）
 */
@Configuration
public class RabbitMQConfig {

    @Value("${seckill.mq.exchange}")
    private String exchange;

    @Value("${seckill.mq.queue}")
    private String queue;

    @Value("${seckill.mq.routing-key}")
    private String routingKey;

    @Value("${seckill.mq.dlq}")
    private String dlq;

    @Value("${seckill.mq.dlx}")
    private String dlx;

    /** 秒杀交换机 */
    @Bean
    public DirectExchange seckillExchange() {
        return ExchangeBuilder.directExchange(exchange).durable(true).build();
    }

    /** 秒杀队列（带死信转发） */
    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(dlx)
                .deadLetterRoutingKey(routingKey)
                .build();
    }

    /** 绑定：交换机 → 队列 */
    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue())
                .to(seckillExchange())
                .with(routingKey);
    }

    /** 死信交换机 */
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(dlx).durable(true).build();
    }

    /** 死信队列 */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(dlq).build();
    }

    /** 绑定：死信交换机 → 死信队列 */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(routingKey);
    }
}
