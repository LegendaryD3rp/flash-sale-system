package com.flashsale.orderservice.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/**
 * 用户优惠券视图对象
 */
public class UserCouponVO {

    private Long id;
    private Long userId;
    private Long couponId;

    /** 优惠券名称（冗余展示） */
    private String couponName;

    /** 优惠券类型 */
    private String type;

    /** 优惠数值 */
    private Long discount;

    /** 最低订单金额 */
    private Long minAmount;

    /** 状态：UNUSED / USED / EXPIRED */
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime claimedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime usedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 是否可用（结算页用） */
    private Boolean available;

    /** 预估折扣金额（结算页用） */
    private Long estimatedDiscount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getCouponId() { return couponId; }
    public void setCouponId(Long couponId) { this.couponId = couponId; }

    public String getCouponName() { return couponName; }
    public void setCouponName(String couponName) { this.couponName = couponName; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Long getDiscount() { return discount; }
    public void setDiscount(Long discount) { this.discount = discount; }

    public Long getMinAmount() { return minAmount; }
    public void setMinAmount(Long minAmount) { this.minAmount = minAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getClaimedAt() { return claimedAt; }
    public void setClaimedAt(LocalDateTime claimedAt) { this.claimedAt = claimedAt; }

    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }

    public Long getEstimatedDiscount() { return estimatedDiscount; }
    public void setEstimatedDiscount(Long estimatedDiscount) { this.estimatedDiscount = estimatedDiscount; }
}
