package com.flashsale.productservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashsale.productservice.entity.ProductReview;
import com.flashsale.productservice.vo.ReviewVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReviewMapper extends BaseMapper<ProductReview> {

    /**
     * 分页查询商品评价（联表查用户昵称）
     */
    IPage<ReviewVO> selectReviewsByProductId(Page<?> page, @Param("productId") Long productId);

    /**
     * 分页查询用户的评价列表
     */
    IPage<ReviewVO> selectReviewsByUserId(Page<?> page, @Param("userId") Long userId);
}
