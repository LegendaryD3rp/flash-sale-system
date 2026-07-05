package com.flashsale.productservice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.flashsale.productservice.vo.ReviewCreateReq;
import com.flashsale.productservice.vo.ReviewVO;

public interface ReviewService {

    /**
     * 新增评价
     */
    Long createReview(Long userId, ReviewCreateReq req);

    /**
     * 查询商品评价列表
     */
    IPage<ReviewVO> getReviewsByProductId(Long productId, int page, int size);

    /**
     * 查询用户的评价列表
     */
    IPage<ReviewVO> getReviewsByUserId(Long userId, int page, int size);

    /**
     * 检查某订单是否已评价
     */
    boolean isOrderReviewed(Long orderId);

    /**
     * 为指定商品创建评价（无需订单ID）
     */
    Long createReviewForProduct(Long userId, Long productId, Integer rating, String content);
}
