package com.flashsale.orderservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashsale.common.constant.ErrorCode;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.orderservice.entity.Coupon;
import com.flashsale.orderservice.entity.UserCoupon;
import com.flashsale.orderservice.mapper.CouponMapper;
import com.flashsale.orderservice.mapper.UserCouponMapper;
import com.flashsale.orderservice.service.CouponService;
import com.flashsale.orderservice.vo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CouponServiceImpl implements CouponService {

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;

    public CouponServiceImpl(CouponMapper couponMapper, UserCouponMapper userCouponMapper) {
        this.couponMapper = couponMapper;
        this.userCouponMapper = userCouponMapper;
    }

    // ==================== 管理端 ====================

    @Override
    public IPage<CouponVO> listCoupons(int page, int size) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Coupon::getId);
        IPage<Coupon> couponPage = couponMapper.selectPage(new Page<>(page, size), wrapper);
        return couponPage.convert(this::toCouponVO);
    }

    @Override
    public CouponVO getCouponById(Long id) {
        Coupon coupon = couponMapper.selectById(id);
        if (coupon == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "优惠券不存在");
        }
        return toCouponVO(coupon);
    }

    @Override
    public void createCoupon(CouponCreateReq req) {
        Coupon coupon = new Coupon();
        coupon.setName(req.getName());
        coupon.setType(req.getType());
        coupon.setDiscount(req.getDiscount());
        coupon.setMinAmount(req.getMinAmount() != null ? req.getMinAmount() : 0L);
        coupon.setStock(req.getStock());
        coupon.setTaken(0);
        coupon.setStartTime(req.getStartTime());
        coupon.setEndTime(req.getEndTime());
        coupon.setStatus("ACTIVE");
        couponMapper.insert(coupon);
    }

    @Override
    public void updateCoupon(Long id, CouponUpdateReq req) {
        Coupon coupon = couponMapper.selectById(id);
        if (coupon == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "优惠券不存在");
        }
        if (req.getName() != null) coupon.setName(req.getName());
        if (req.getType() != null) coupon.setType(req.getType());
        if (req.getDiscount() != null) coupon.setDiscount(req.getDiscount());
        if (req.getMinAmount() != null) coupon.setMinAmount(req.getMinAmount());
        if (req.getStock() != null) coupon.setStock(req.getStock());
        if (req.getStartTime() != null) coupon.setStartTime(req.getStartTime());
        if (req.getEndTime() != null) coupon.setEndTime(req.getEndTime());
        couponMapper.updateById(coupon);
    }

    @Override
    public void disableCoupon(Long id) {
        Coupon coupon = couponMapper.selectById(id);
        if (coupon == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "优惠券不存在");
        }
        coupon.setStatus("DISABLED");
        couponMapper.updateById(coupon);
    }

    // ==================== 用户端 ====================

    @Override
    @Transactional
    public UserCouponVO claimCoupon(Long userId, Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "优惠券不存在");
        }
        // 校验状态
        if (!"ACTIVE".equals(coupon.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "优惠券已失效");
        }
        // 校验有效期
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())) {
            throw new BusinessException(ErrorCode.CONFLICT, "不在领取时间段内");
        }
        // 校验库存
        if (coupon.getTaken() >= coupon.getStock()) {
            throw new BusinessException(ErrorCode.CONFLICT, "优惠券已领完");
        }
        // 校验去重
        LambdaQueryWrapper<UserCoupon> dupCheck = new LambdaQueryWrapper<>();
        dupCheck.eq(UserCoupon::getUserId, userId);
        dupCheck.eq(UserCoupon::getCouponId, couponId);
        if (userCouponMapper.selectCount(dupCheck) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "已领取过该优惠券");
        }

        // 原子扣减库存
        int rows = couponMapper.incrementTaken(couponId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "优惠券已领完");
        }

        // 插入用户优惠券
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        userCoupon.setStatus("UNUSED");
        userCoupon.setClaimedAt(now);
        userCouponMapper.insert(userCoupon);

        return toUserCouponVO(userCoupon, coupon);
    }

    @Override
    public IPage<UserCouponVO> myCoupons(Long userId, int page, int size) {
        // 分页查询用户优惠券
        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCoupon::getUserId, userId);
        wrapper.orderByDesc(UserCoupon::getId);
        IPage<UserCoupon> ucPage = userCouponMapper.selectPage(new Page<>(page, size), wrapper);

        // 批量查询关联的优惠券信息
        List<UserCoupon> records = ucPage.getRecords();
        List<Long> couponIds = records.stream().map(UserCoupon::getCouponId).collect(Collectors.toList());
        List<Coupon> coupons = couponMapper.selectBatchIds(couponIds);
        java.util.Map<Long, Coupon> couponMap = coupons.stream()
                .collect(Collectors.toMap(Coupon::getId, c -> c));

        return ucPage.convert(uc -> {
            Coupon c = couponMap.get(uc.getCouponId());
            UserCouponVO vo = toUserCouponVO(uc, c);
            // 已过期但状态未更新的，修正状态
            if ("UNUSED".equals(vo.getStatus()) && c != null
                    && LocalDateTime.now().isAfter(c.getEndTime())) {
                vo.setStatus("EXPIRED");
            }
            return vo;
        });
    }

    @Override
    public List<UserCouponVO> availableCoupons(Long userId, Long amount) {
        // 查询用户所有 UNUSED 的优惠券
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCoupon::getUserId, userId);
        wrapper.eq(UserCoupon::getStatus, "UNUSED");
        List<UserCoupon> list = userCouponMapper.selectList(wrapper);

        List<UserCouponVO> result = new ArrayList<>();
        for (UserCoupon uc : list) {
            Coupon coupon = couponMapper.selectById(uc.getCouponId());
            if (coupon == null) continue;
            // 校验优惠券是否有效
            if (!"ACTIVE".equals(coupon.getStatus())) continue;
            if (now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())) continue;

            UserCouponVO vo = toUserCouponVO(uc, coupon);
            // 校验门槛
            if (amount >= coupon.getMinAmount()) {
                vo.setAvailable(true);
                vo.setEstimatedDiscount(calculateDiscount(coupon, amount));
            } else {
                vo.setAvailable(false);
                vo.setEstimatedDiscount(0L);
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public Long calcDiscount(Long userCouponId, Long amount) {
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "优惠券不存在");
        }
        if (!"UNUSED".equals(uc.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "优惠券已使用或已过期");
        }
        Coupon coupon = couponMapper.selectById(uc.getCouponId());
        if (coupon == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "优惠券模板不存在");
        }
        if (!"ACTIVE".equals(coupon.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "优惠券已失效");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())) {
            throw new BusinessException(ErrorCode.CONFLICT, "优惠券不在有效期内");
        }
        if (amount < coupon.getMinAmount()) {
            throw new BusinessException(ErrorCode.CONFLICT, "未达到最低使用金额");
        }
        return calculateDiscount(coupon, amount);
    }

    @Override
    @Transactional
    public void useCoupon(Long userCouponId, Long orderId) {
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "优惠券不存在");
        }
        if (!"UNUSED".equals(uc.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "优惠券不可用");
        }
        uc.setStatus("USED");
        uc.setUsedAt(LocalDateTime.now());
        uc.setOrderId(orderId);
        userCouponMapper.updateById(uc);
    }

    @Override
    @Transactional
    public void releaseCoupon(Long userCouponId) {
        if (userCouponId == null) return;
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null) return;
        if (!"USED".equals(uc.getStatus())) return;
        uc.setStatus("UNUSED");
        uc.setUsedAt(null);
        uc.setOrderId(null);
        userCouponMapper.updateById(uc);
    }

    @Override
    public Long getCouponIdByUserCouponId(Long userCouponId) {
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户优惠券不存在");
        }
        return uc.getCouponId();
    }

    // ==================== 私有方法 ====================

    private Long calculateDiscount(Coupon coupon, Long amount) {
        if ("PERCENT".equals(coupon.getType())) {
            // 百分比优惠，最高99%
            long discount = amount * coupon.getDiscount() / 100;
            return Math.min(discount, amount); // 防止超过订单金额
        } else {
            // 固定金额优惠
            return Math.min(coupon.getDiscount(), amount); // 不超过订单金额
        }
    }

    private CouponVO toCouponVO(Coupon coupon) {
        CouponVO vo = new CouponVO();
        vo.setId(coupon.getId());
        vo.setName(coupon.getName());
        vo.setType(coupon.getType());
        vo.setDiscount(coupon.getDiscount());
        vo.setMinAmount(coupon.getMinAmount());
        vo.setStock(coupon.getStock());
        vo.setTaken(coupon.getTaken());
        vo.setStatus(coupon.getStatus());
        vo.setStartTime(coupon.getStartTime());
        vo.setEndTime(coupon.getEndTime());
        return vo;
    }

    private UserCouponVO toUserCouponVO(UserCoupon uc, Coupon coupon) {
        UserCouponVO vo = new UserCouponVO();
        vo.setId(uc.getId());
        vo.setUserId(uc.getUserId());
        vo.setCouponId(uc.getCouponId());
        vo.setStatus(uc.getStatus());
        vo.setClaimedAt(uc.getClaimedAt());
        vo.setUsedAt(uc.getUsedAt());
        if (coupon != null) {
            vo.setCouponName(coupon.getName());
            vo.setType(coupon.getType());
            vo.setDiscount(coupon.getDiscount());
            vo.setMinAmount(coupon.getMinAmount());
            vo.setStartTime(coupon.getStartTime());
            vo.setEndTime(coupon.getEndTime());
        }
        vo.setAvailable(false);
        vo.setEstimatedDiscount(0L);
        return vo;
    }
}
