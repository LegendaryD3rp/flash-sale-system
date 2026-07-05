package com.flashsale.orderservice.vo;

/**
 * 数据概览
 */
public class StatisticsSummaryVO {

    private long todayOrderCount;
    private long todaySalesAmount;
    private long totalUsers;
    private long totalProducts;
    private long totalOrders;

    public long getTodayOrderCount() { return todayOrderCount; }
    public void setTodayOrderCount(long todayOrderCount) { this.todayOrderCount = todayOrderCount; }

    public long getTodaySalesAmount() { return todaySalesAmount; }
    public void setTodaySalesAmount(long todaySalesAmount) { this.todaySalesAmount = todaySalesAmount; }

    public long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(long totalUsers) { this.totalUsers = totalUsers; }

    public long getTotalProducts() { return totalProducts; }
    public void setTotalProducts(long totalProducts) { this.totalProducts = totalProducts; }

    public long getTotalOrders() { return totalOrders; }
    public void setTotalOrders(long totalOrders) { this.totalOrders = totalOrders; }
}
