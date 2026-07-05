package com.flashsale.orderservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.common.dto.SeckillMessage;
import com.flashsale.common.websocket.SeckillWebSocketHandler;
import com.flashsale.orderservice.entity.Order;
import com.flashsale.orderservice.mapper.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 秒杀落单消费者 — 位于订单服务（正确架构）
 *
 * 监听 seckill.order.queue，将秒杀成功的订单写入 order 表。
 * 写入成功后通过 WebSocket 推送结果给用户。
 *
 * 削峰机制：
 *   - prefetch=10  （application.yml 配置）
 *   - concurrency=3-8（application.yml 配置）
 *   - 重试3次 → 死信队列兜底
 */
@Component
public class SeckillOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderConsumer.class);

    private final ObjectMapper objectMapper;
    private final OrderMapper orderMapper;

    public SeckillOrderConsumer(ObjectMapper objectMapper, OrderMapper orderMapper) {
        this.objectMapper = objectMapper;
        this.orderMapper = orderMapper;
    }

    @RabbitListener(queues = "${seckill.mq.queue}")
    public void handleSeckillOrder(String messageJson) {
        SeckillMessage msg;
        try {
            msg = objectMapper.readValue(messageJson, SeckillMessage.class);
        } catch (Exception e) {
            log.error("Failed to parse SeckillMessage: {}", messageJson, e);
            return;
        }

        Long orderSn = msg.getOrderSn();
        Long userId = msg.getUserId();
        Long activityId = msg.getActivityId();
        Long productId = msg.getProductId();
        Long seckillPrice = msg.getSeckillPrice();

        log.info("Consumer received — orderSn={}, userId={}, activityId={}", orderSn, userId, activityId);

        try {
            // ① 幂等校验
            if (orderMapper.selectById(orderSn) != null) {
                log.warn("Order already exists — orderSn={}, skipping", orderSn);
                return;
            }

            // ② 构建订单实体并使用 MyBatis-Plus 写入
            LocalDateTime now = LocalDateTime.now();
            Order order = new Order();
            order.setId(orderSn);
            order.setUserId(userId);
            order.setProductId(productId);
            order.setSeckillActivityId(activityId);
            order.setSeckillPrice(seckillPrice);
            order.setQuantity(1);
            order.setTotalAmount(seckillPrice);
            order.setStatus("SUCCESS");
            order.setCreatedAt(now);
            order.setUpdatedAt(now);

            orderMapper.insert(order);

            // ③ 更新秒杀活动可用库存
            // （seckill-service 的 Redis 已原子扣减，此处仅同步 DB 状态）
            // 通过 Mapper 直接 SQL update 简化依赖
            orderMapper.updateSeckillStock(activityId);

            // ④ WebSocket 推送成功结果
            String pushMsg = String.format(
                    "{\"type\":\"seckill_result\",\"status\":\"SUCCESS\",\"orderSn\":%d,\"activityId\":%d}",
                    orderSn, activityId);
            SeckillWebSocketHandler.pushToUser(userId, pushMsg);

            log.info("Order created successfully — orderSn={}", orderSn);

        } catch (DuplicateKeyException e) {
            log.warn("Duplicate order — orderSn={}", orderSn);
        } catch (Exception e) {
            log.error("Failed to create order — orderSn={}", orderSn, e);
            // 推送失败通知
            String failMsg = String.format(
                    "{\"type\":\"seckill_result\",\"status\":\"FAILED\",\"orderSn\":%d,\"activityId\":%d,\"message\":\"系统异常\"}",
                    orderSn, activityId);
            SeckillWebSocketHandler.pushToUser(userId, failMsg);
            // 抛出异常 → 触发 Spring AMQP 重试 → 最终进死信队列
            throw new RuntimeException(e);
        }
    }
}
