package com.flashsale.productservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.flashsale.common.result.Result;
import com.flashsale.productservice.service.ReviewService;
import com.flashsale.productservice.vo.ReviewCreateReq;
import com.flashsale.productservice.vo.ReviewVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/product")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * POST /api/product/review — 新增评价
     */
    @PostMapping("/review")
    public Result<Long> createReview(
            @Valid @RequestBody ReviewCreateReq req,
            @RequestHeader("X-User-Id") Long userId) {
        Long id = reviewService.createReview(userId, req);
        return Result.success(id);
    }

    /**
     * POST /api/product/{productId}/reviews — 对指定商品发表评价（无需订单ID）
     */
    @PostMapping("/{productId}/reviews")
    public Result<Long> createReviewForProduct(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-User-Id") Long userId) {
        Integer rating = body.get("rating") != null ? ((Number) body.get("rating")).intValue() : 5;
        String content = body.get("content") != null ? body.get("content").toString() : "";
        Long id = reviewService.createReviewForProduct(userId, productId, rating, content);
        return Result.success(id);
    }

    /**
     * GET /api/product/{productId}/reviews — 查商品评价列表
     */
    @GetMapping("/{productId}/reviews")
    public Result<IPage<ReviewVO>> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        IPage<ReviewVO> result = reviewService.getReviewsByProductId(productId, page, size);
        return Result.success(result);
    }

    /**
     * GET /api/product/review/check?orderId=xxx — 检查某订单是否已评价
     */
    @GetMapping("/review/check")
    public Result<Map<String, Boolean>> checkReview(@RequestParam Long orderId) {
        boolean reviewed = reviewService.isOrderReviewed(orderId);
        return Result.success(Map.of("reviewed", reviewed));
    }

    /**
     * GET /api/product/review/mine — 我的评价列表
     */
    @GetMapping("/review/mine")
    public Result<IPage<ReviewVO>> myReviews(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("X-User-Id") Long userId) {
        IPage<ReviewVO> result = reviewService.getReviewsByUserId(userId, page, size);
        return Result.success(result);
    }
}
