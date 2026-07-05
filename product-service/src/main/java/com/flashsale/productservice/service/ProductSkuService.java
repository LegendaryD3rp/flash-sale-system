package com.flashsale.productservice.service;

import com.flashsale.productservice.vo.ProductSkuVO;
import java.util.List;

public interface ProductSkuService {

    /**
     * 根据商品ID查询SKU列表
     */
    List<ProductSkuVO> findByProductId(Long productId);

    /**
     * 批量保存SKU（先删后插）
     */
    void saveSkus(Long productId, List<ProductSkuVO> skus);
}
