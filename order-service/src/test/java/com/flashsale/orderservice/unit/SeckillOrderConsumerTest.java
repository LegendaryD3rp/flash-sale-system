package com.flashsale.orderservice.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.common.dto.SeckillMessage;
import com.flashsale.orderservice.consumer.SeckillOrderConsumer;
import com.flashsale.orderservice.entity.Order;
import com.flashsale.orderservice.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SeckillOrderConsumer 单元测试 — 验证 MQ 消息消费逻辑
 *
 * 测试点：
 *   P0-⑥ 正常消费：MQ 消息 → 创建订单
 *   P0-⑦ 重复消息幂等：同一条消息消费两次，只创建一个订单
 *   P0-⑧ 库存不足时消费失败，消息不丢失（requeue 或 DLQ）
 */
class SeckillOrderConsumerTest {

    private ObjectMapper objectMapper;
    private OrderMapper orderMapper;
    private SeckillOrderConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        orderMapper = mock(OrderMapper.class);
        consumer = new SeckillOrderConsumer(objectMapper, orderMapper);
    }

    private String validMessage() throws Exception {
        SeckillMessage msg = new SeckillMessage(1L, 1L, 10L, 9900L, 10001L);
        return objectMapper.writeValueAsString(msg);
    }

    /** ⭐ P0-⑥ 正常消费：MQ 消息 → 创建订单 */
    @Test
    void handleSeckillOrder_ShouldCreateOrder() throws Exception {
        when(orderMapper.selectById(10001L)).thenReturn(null);
        when(orderMapper.insert((Order) any())).thenReturn(1);

        consumer.handleSeckillOrder(validMessage());

        verify(orderMapper).insert((Order) any());
    }

    /** ⭐ P0-⑦ 重复消息幂等：同一条消息消费两次，只创建一个订单 */
    @Test
    void handleSeckillOrder_ShouldSkip_WhenOrderExists() throws Exception {
        Order existing = new Order();
        existing.setId(10001L);
        when(orderMapper.selectById(10001L)).thenReturn(existing);

        consumer.handleSeckillOrder(validMessage());

        verify(orderMapper, never()).insert((Order) any());
    }

    /** ⭐ P0-⑧ 消息格式异常 → 静默返回（不抛出异常，避免无限重试） */
    @Test
    void handleSeckillOrder_ShouldSwallow_WhenInvalidJson() {
        consumer.handleSeckillOrder("not-a-valid-json");
        verify(orderMapper, never()).insert((Order) any());
    }

    /** ⭐ P0-⑧ 唯一键冲突（重复消息）→ 静默吞掉，不抛异常（不 requeue） */
    @Test
    void handleSeckillOrder_ShouldHandle_WhenDuplicateKey() throws Exception {
        when(orderMapper.selectById(10001L)).thenReturn(null);
        when(orderMapper.insert((Order) any())).thenThrow(new DuplicateKeyException("Duplicate entry"));

        // Should not re-throw
        consumer.handleSeckillOrder(validMessage());
    }
}
