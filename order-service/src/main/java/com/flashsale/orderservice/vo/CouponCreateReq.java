package com.flashsale.orderservice.vo;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/**
 * 新建优惠券请求
 */
public class CouponCreateReq {

    @NotBlank(message = "优惠券名称不能为空")
    private String name;

    @NotBlank(message = "优惠券类型不能为空")
    private String type;

    @NotNull(message = "优惠数值不能为空")
    @Min(value = 1, message = "优惠数值至少1")
    private Long discount;

    private Long minAmount;

    @NotNull(message = "库存不能为空")
    @Min(value = 1, message = "库存至少1")
    private Integer stock;

    @NotNull(message = "生效时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "失效时间不能为空")
    private LocalDateTime endTime;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Long getDiscount() { return discount; }
    public void setDiscount(Long discount) { this.discount = discount; }

    public Long getMinAmount() { return minAmount; }
    public void setMinAmount(Long minAmount) { this.minAmount = minAmount; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}
