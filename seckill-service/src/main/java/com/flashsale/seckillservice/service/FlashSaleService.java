package com.flashsale.seckillservice.service;

/**
 * 秒杀抢购核心服务
 */
public interface FlashSaleService {

    /**
     * 执行秒杀抢购
     *
     * @param activityId 活动ID
     * @param userId     用户ID
     * @return "排队中" 表示请求已受理，结果将通过 WebSocket 异步推送
     */
    String flash(Long activityId, Long userId);
}
