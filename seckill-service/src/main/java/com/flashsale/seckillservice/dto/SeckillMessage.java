package com.flashsale.seckillservice.dto;

import java.io.Serializable;

/**
 * 秒杀落单 MQ 消息体
 */
public class SeckillMessage implements Serializable {

    private Long userId;
    private Long activityId;
    private Long productId;
    private Long seckillPrice;
    private Long orderSn;       // 雪花算法生成的订单号
    private long timestamp;

    public SeckillMessage() {}

    public SeckillMessage(Long userId, Long activityId, Long productId,
                          Long seckillPrice, Long orderSn) {
        this.userId = userId;
        this.activityId = activityId;
        this.productId = productId;
        this.seckillPrice = seckillPrice;
        this.orderSn = orderSn;
        this.timestamp = System.currentTimeMillis();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getSeckillPrice() { return seckillPrice; }
    public void setSeckillPrice(Long seckillPrice) { this.seckillPrice = seckillPrice; }

    public Long getOrderSn() { return orderSn; }
    public void setOrderSn(Long orderSn) { this.orderSn = orderSn; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
