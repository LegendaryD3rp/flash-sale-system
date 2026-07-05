package com.flashsale.productservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashsale.common.constant.ErrorCode;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.productservice.entity.ProductReview;
import com.flashsale.productservice.mapper.ReviewMapper;
import com.flashsale.productservice.service.ReviewService;
import com.flashsale.productservice.vo.ReviewCreateReq;
import com.flashsale.productservice.vo.ReviewVO;
import org.springframework.stereotype.Service;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;

    public ReviewServiceImpl(ReviewMapper reviewMapper) {
        this.reviewMapper = reviewMapper;
    }

    @Override
    public Long createReview(Long userId, ReviewCreateReq req) {
        // 检查是否已评价过该订单
        if (isOrderReviewed(req.getOrderId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该订单已评价");
        }

        ProductReview review = new ProductReview();
        review.setProductId(req.getProductId());
        review.setUserId(userId);
        review.setOrderId(req.getOrderId());
        review.setRating(req.getRating());
        review.setContent(req.getContent());
        review.setImages(req.getImages());

        reviewMapper.insert(review);
        return review.getId();
    }

    @Override
    public IPage<ReviewVO> getReviewsByProductId(Long productId, int page, int size) {
        IPage<ReviewVO> result = reviewMapper.selectReviewsByProductId(new Page<>(page, size), productId);
        // 处理晒图字段（逗号分隔 → 数组）
        result.getRecords().forEach(this::processImages);
        return result;
    }

    @Override
    public IPage<ReviewVO> getReviewsByUserId(Long userId, int page, int size) {
        IPage<ReviewVO> result = reviewMapper.selectReviewsByUserId(new Page<>(page, size), userId);
        result.getRecords().forEach(this::processImages);
        return result;
    }

    @Override
    public boolean isOrderReviewed(Long orderId) {
        LambdaQueryWrapper<ProductReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductReview::getOrderId, orderId);
        return reviewMapper.selectCount(wrapper) > 0;
    }

    @Override
    public Long createReviewForProduct(Long userId, Long productId, Integer rating, String content) {
        ProductReview review = new ProductReview();
        review.setProductId(productId);
        review.setUserId(userId);
        review.setRating(rating);
        review.setContent(content);

        reviewMapper.insert(review);
        return review.getId();
    }

    private void processImages(ReviewVO vo) {
        // images 字段存储为逗号分隔的URL，转换为数组
        // 这里不做转换，交由前端处理
    }
}
