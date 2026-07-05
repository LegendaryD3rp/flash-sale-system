package com.flashsale.seckillservice.vo;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

/**
 * 创建秒杀活动请求
 */
public class SeckillActivityCreateReq {

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @NotNull(message = "秒杀价不能为空")
    @Positive(message = "秒杀价必须大于0")
    private Long seckillPrice;

    @NotNull(message = "秒杀总库存不能为空")
    @Positive(message = "秒杀总库存必须大于0")
    private Integer totalStock;

    @NotNull(message = "开始时间不能为空")
    @Future(message = "开始时间必须在未来")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    @Future(message = "结束时间必须在未来")
    private LocalDateTime endTime;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getSeckillPrice() { return seckillPrice; }
    public void setSeckillPrice(Long seckillPrice) { this.seckillPrice = seckillPrice; }

    public Integer getTotalStock() { return totalStock; }
    public void setTotalStock(Integer totalStock) { this.totalStock = totalStock; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}
