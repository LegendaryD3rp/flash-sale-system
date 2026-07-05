package com.flashsale.productservice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.flashsale.productservice.vo.*;

public interface ProductService {

    /**
     * 分页查询商品列表
     * CUSTOMER 角色只查 status=ON 的商品
     */
    IPage<ProductVO> listProducts(int page, int size, String keyword, String category,
                                  Long minPrice, Long maxPrice, String role);

    /**
     * 搜索商品（名称 + 描述模糊匹配）
     */
    IPage<ProductVO> search(int page, int size, String keyword);

    /**
     * 查询商品详情
     */
    ProductVO getProductById(Long id);

    /**
     * 新增商品
     */
    Long createProduct(ProductCreateReq req);

    /**
     * 编辑商品（部分更新）
     */
    Long updateProduct(Long id, ProductUpdateReq req);

    /**
     * 下架商品（逻辑删除：status → OFF）
     */
    Long deleteProduct(Long id);
}
