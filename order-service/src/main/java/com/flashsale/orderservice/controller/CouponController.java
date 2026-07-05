package com.flashsale.orderservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.flashsale.common.annotation.AuditLog;
import com.flashsale.common.constant.ErrorCode;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.common.result.Result;
import com.flashsale.orderservice.service.CouponService;
import com.flashsale.orderservice.vo.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    // ==================== 管理端 ====================

    /**
     * GET /api/admin/coupon/list — 优惠券列表
     */
    @GetMapping("/api/admin/coupon/list")
    @AuditLog(module = "优惠券管理", action = "查询", description = "查询优惠券列表")
    public Result<IPage<CouponVO>> listCoupons(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Role", defaultValue = "CUSTOMER") String role) {
        checkAdmin(role);
        return Result.success(couponService.listCoupons(page, size));
    }

    /**
     * GET /api/admin/coupon/{id} — 优惠券详情
     */
    @GetMapping("/api/admin/coupon/{id}")
    @AuditLog(module = "优惠券管理", action = "查询", description = "查看优惠券 {id}")
    public Result<CouponVO> getCoupon(@PathVariable Long id,
                                      @RequestHeader("X-User-Role") String role) {
        checkAdmin(role);
        return Result.success(couponService.getCouponById(id));
    }

    /**
     * POST /api/admin/coupon — 新建优惠券
     */
    @PostMapping("/api/admin/coupon")
    @AuditLog(module = "优惠券管理", action = "新增", description = "新建优惠券")
    public Result<Void> createCoupon(@Valid @RequestBody CouponCreateReq req,
                                     @RequestHeader("X-User-Role") String role) {
        checkAdmin(role);
        couponService.createCoupon(req);
        return Result.success();
    }

    /**
     * PUT /api/admin/coupon/{id} — 编辑优惠券
     */
    @PutMapping("/api/admin/coupon/{id}")
    @AuditLog(module = "优惠券管理", action = "修改", description = "编辑优惠券 {id}")
    public Result<Void> updateCoupon(@PathVariable Long id,
                                     @RequestBody CouponUpdateReq req,
                                     @RequestHeader("X-User-Role") String role) {
        checkAdmin(role);
        couponService.updateCoupon(id, req);
        return Result.success();
    }

    /**
     * PUT /api/admin/coupon/{id}/disable — 下架优惠券
     */
    @PutMapping("/api/admin/coupon/{id}/disable")
    @AuditLog(module = "优惠券管理", action = "删除", description = "下架优惠券 {id}")
    public Result<Void> disableCoupon(@PathVariable Long id,
                                      @RequestHeader("X-User-Role") String role) {
        checkAdmin(role);
        couponService.disableCoupon(id);
        return Result.success();
    }

    // ==================== 用户端 ====================

    /**
     * POST /api/coupon/claim/{couponId} — 领券
     */
    @PostMapping("/api/coupon/claim/{couponId}")
    public Result<UserCouponVO> claimCoupon(
            @PathVariable Long couponId,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(couponService.claimCoupon(userId, couponId));
    }

    /**
     * GET /api/coupon/mine — 我的优惠券
     */
    @GetMapping("/api/coupon/mine")
    public Result<IPage<UserCouponVO>> myCoupons(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(couponService.myCoupons(userId, page, size));
    }

    /**
     * GET /api/coupon/available?amount= — 结算时可用的券
     */
    @GetMapping("/api/coupon/available")
    public Result<List<UserCouponVO>> availableCoupons(
            @RequestParam Long amount,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(couponService.availableCoupons(userId, amount));
    }

    /**
     * POST /api/coupon/calc-discount — 计算优惠金额
     */
    @PostMapping("/api/coupon/calc-discount")
    public Result<Map<String, Long>> calcDiscount(
            @Valid @RequestBody CalcDiscountReq req) {
        Long discount = couponService.calcDiscount(req.getUserCouponId(), req.getAmount());
        return Result.success(Map.of("discount", discount));
    }

    private void checkAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作");
        }
    }
}
