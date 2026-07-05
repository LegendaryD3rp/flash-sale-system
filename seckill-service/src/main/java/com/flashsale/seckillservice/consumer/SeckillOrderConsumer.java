package com.flashsale.seckillservice.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.seckillservice.dto.SeckillMessage;
import com.flashsale.seckillservice.websocket.SeckillWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 秒杀落单消费者
 *
 * 监听 seckill.order.queue，将秒杀成功的订单写入数据库。
 * 结果通过 WebSocket 推送给用户。
 */
@Component
public class SeckillOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderConsumer.class);

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public SeckillOrderConsumer(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @RabbitListener(queues = "${seckill.mq.queue}")
    public void handleSeckillOrder(String messageJson) {
        SeckillMessage msg;
        try {
            msg = objectMapper.readValue(messageJson, SeckillMessage.class);
        } catch (Exception e) {
            log.error("Failed to parse SeckillMessage: {}", messageJson, e);
            return; // skip malformed messages
        }

        Long orderSn = msg.getOrderSn();
        Long userId = msg.getUserId();
        Long activityId = msg.getActivityId();
        Long productId = msg.getProductId();
        Long seckillPrice = msg.getSeckillPrice();

        log.info("Consumer received — orderSn={}, userId={}, activityId={}", orderSn, userId, activityId);

        try {
            // ① 幂等校验：订单号是否已存在
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `order` WHERE id = ?", Integer.class, orderSn);
            if (count != null && count > 0) {
                log.warn("Order already exists — orderSn={}, skipping", orderSn);
                return;
            }

            // ② 插入订单
            LocalDateTime now = LocalDateTime.now();
            jdbcTemplate.update(
                    "INSERT INTO `order` (id, user_id, product_id, seckill_activity_id, " +
                    "seckill_price, quantity, total_amount, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, 1, ?, 'SUCCESS', ?, ?)",
                    orderSn, userId, productId, activityId,
                    seckillPrice, seckillPrice, now, now);

            // ③ 更新可用库存
            jdbcTemplate.update(
                    "UPDATE seckill_activity SET available_stock = available_stock - 1 WHERE id = ? AND available_stock > 0",
                    activityId);

            // ④ 推送 WebSocket 通知（成功）
            pushWebSocketMessage(userId, activityId, orderSn, "SUCCESS", "抢购成功！");

            log.info("Order created successfully — orderSn={}", orderSn);

        } catch (DuplicateKeyException e) {
            log.warn("Duplicate order — orderSn={}", orderSn);
        } catch (Exception e) {
            log.error("Failed to create order — orderSn={}", orderSn, e);
            // ⑤ 异常情况：推送失败通知
            pushWebSocketMessage(userId, activityId, orderSn, "FAILED", "系统异常，订单创建失败");
        }
    }

    /**
     * 通过 WebSocket 推送抢购结果给用户
     */
    private void pushWebSocketMessage(Long userId, Long activityId, Long orderSn,
                                      String status, String message) {
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("type", "seckill_result");
            payload.put("activityId", activityId);
            payload.put("orderSn", orderSn);
            payload.put("status", status);
            payload.put("message", message);
            payload.put("timestamp", System.currentTimeMillis());
            String json = objectMapper.writeValueAsString(payload);
            SeckillWebSocketHandler.pushToUser(userId, json);
            log.info("WebSocket push OK — userId={}, activityId={}, orderSn={}, status={}",
                    userId, activityId, orderSn, status);
        } catch (JsonProcessingException e) {
            log.error("WebSocket push failed — failed to serialize payload", e);
        }
    }
}
