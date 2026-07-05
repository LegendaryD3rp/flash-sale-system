package com.flashsale.seckillservice.vo;

import jakarta.validation.constraints.NotNull;

/**
 * 秒杀抢购请求体
 */
public class SeckillRequest {

    @NotNull(message = "活动ID不能为空")
    private Long activityId;

    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
}
