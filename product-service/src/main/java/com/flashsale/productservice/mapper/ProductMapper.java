package com.flashsale.productservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashsale.productservice.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 搜索商品（名称 + 描述模糊匹配）
     * @param page 分页参数
     * @param keyword 搜索关键词
     * @return 分页商品列表
     */
    IPage<Product> searchProducts(Page<?> page, @Param("keyword") String keyword);
}
