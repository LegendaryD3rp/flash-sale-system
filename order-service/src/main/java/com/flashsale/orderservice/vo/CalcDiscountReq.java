package com.flashsale.orderservice.vo;

import jakarta.validation.constraints.NotNull;

/**
 * 计算优惠请求
 */
public class CalcDiscountReq {

    @NotNull(message = "用户优惠券ID不能为空")
    private Long userCouponId;

    @NotNull(message = "订单金额不能为空")
    private Long amount;

    public Long getUserCouponId() { return userCouponId; }
    public void setUserCouponId(Long userCouponId) { this.userCouponId = userCouponId; }

    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
}
