package com.flashsale.orderservice.vo;

/**
 * 订单趋势（每日）
 */
public class OrderTrendVO {

    private String date;
    private long count;
    private long amount;

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }

    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
}
