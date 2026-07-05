package com.flashsale.orderservice.vo;

/**
 * 热销商品
 */
public class TopProductVO {

    private Long productId;
    private String name;
    private long salesCount;
    private long salesAmount;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getSalesCount() { return salesCount; }
    public void setSalesCount(long salesCount) { this.salesCount = salesCount; }

    public long getSalesAmount() { return salesAmount; }
    public void setSalesAmount(long salesAmount) { this.salesAmount = salesAmount; }
}
