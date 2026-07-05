package com.flashsale.orderservice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.flashsale.orderservice.vo.*;

import java.util.List;

public interface CouponService {

    // ===== 管理端 =====
    IPage<CouponVO> listCoupons(int page, int size);
    CouponVO getCouponById(Long id);
    void createCoupon(CouponCreateReq req);
    void updateCoupon(Long id, CouponUpdateReq req);
    void disableCoupon(Long id);

    // ===== 用户端 =====
    UserCouponVO claimCoupon(Long userId, Long couponId);
    IPage<UserCouponVO> myCoupons(Long userId, int page, int size);
    List<UserCouponVO> availableCoupons(Long userId, Long amount);
    Long calcDiscount(Long userCouponId, Long amount);
    void useCoupon(Long userCouponId, Long orderId);
    void releaseCoupon(Long userCouponId);

    /**
     * 根据 userCouponId 获取对应的 couponId
     */
    Long getCouponIdByUserCouponId(Long userCouponId);
}
