package com.flashsale.productservice.service;

import com.flashsale.productservice.vo.ProductImageVO;
import java.util.List;

public interface ProductImageService {

    /**
     * 根据商品ID查询图片列表，按 sortOrder 排序
     */
    List<ProductImageVO> findByProductId(Long productId);

    /**
     * 批量保存商品图片（先删后插）
     */
    void saveImages(Long productId, List<ProductImageVO> images);
}
