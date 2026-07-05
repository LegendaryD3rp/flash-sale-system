package com.flashsale.seckillservice.vo;

import java.time.LocalDateTime;

/**
 * 编辑秒杀活动请求（部分更新）
 */
public class SeckillActivityUpdateReq {

    private Long seckillPrice;
    private Integer totalStock;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public Long getSeckillPrice() { return seckillPrice; }
    public void setSeckillPrice(Long seckillPrice) { this.seckillPrice = seckillPrice; }

    public Integer getTotalStock() { return totalStock; }
    public void setTotalStock(Integer totalStock) { this.totalStock = totalStock; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}
