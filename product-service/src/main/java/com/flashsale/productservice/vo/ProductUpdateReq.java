package com.flashsale.productservice.vo;

import java.util.List;

/**
 * 编辑商品请求体（部分更新）
 */
public class ProductUpdateReq {

    private String name;
    private String description;
    private Long price;
    private String imageUrl;
    private String category;
    private Integer stock;
    private List<ProductImageVO> images;
    private List<ProductSkuVO> skus;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getPrice() { return price; }
    public void setPrice(Long price) { this.price = price; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public List<ProductImageVO> getImages() { return images; }
    public void setImages(List<ProductImageVO> images) { this.images = images; }

    public List<ProductSkuVO> getSkus() { return skus; }
    public void setSkus(List<ProductSkuVO> skus) { this.skus = skus; }
}
