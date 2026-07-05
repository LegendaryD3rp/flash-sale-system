package com.flashsale.orderservice.unit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.orderservice.entity.Coupon;
import com.flashsale.orderservice.entity.UserCoupon;
import com.flashsale.orderservice.mapper.CouponMapper;
import com.flashsale.orderservice.mapper.UserCouponMapper;
import com.flashsale.orderservice.service.impl.CouponServiceImpl;
import com.flashsale.orderservice.vo.CouponVO;
import com.flashsale.orderservice.vo.UserCouponVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    @Mock
    private CouponMapper couponMapper;
    @Mock
    private UserCouponMapper userCouponMapper;

    @InjectMocks
    private CouponServiceImpl couponService;

    private Coupon validCoupon() {
        Coupon c = new Coupon();
        c.setId(1L);
        c.setName("满100减10");
        c.setDiscount(1000L);
        c.setMinAmount(10000L);
        c.setStatus("ACTIVE");
        c.setStartTime(LocalDateTime.now().minusDays(1));
        c.setEndTime(LocalDateTime.now().plusDays(1));
        c.setStock(100);
        c.setTaken(10);
        return c;
    }

    @Test
    void getCouponById_ShouldReturn_WhenExists() {
        Coupon c = validCoupon();
        when(couponMapper.selectById(1L)).thenReturn(c);

        CouponVO vo = couponService.getCouponById(1L);
        assertThat(vo.getName()).isEqualTo("满100减10");
    }

    @Test
    void getCouponById_ShouldThrow_WhenNotExists() {
        when(couponMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> couponService.getCouponById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void claimCoupon_ShouldInsertUserCoupon() {
        Coupon c = validCoupon();
        when(couponMapper.selectById(1L)).thenReturn(c);
        when(userCouponMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(couponMapper.incrementTaken(1L)).thenAnswer(inv -> 1);
        when(userCouponMapper.insert(any(UserCoupon.class))).thenReturn(1);

        UserCouponVO vo = couponService.claimCoupon(1L, 1L);
        assertThat(vo.getUserId()).isEqualTo(1L);
    }

    /** ⭐ P1-⑮ 同一用户不重复领券 */
    @Test
    void claimCoupon_ShouldThrow_WhenAlreadyClaimed() {
        Coupon c = validCoupon();
        when(couponMapper.selectById(1L)).thenReturn(c);
        when(userCouponMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> couponService.claimCoupon(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已领取");
    }

    /** ⭐ P1-⑭ 乐观锁防超发：taken >= stock 时抛异常 */
    @Test
    void claimCoupon_ShouldThrow_WhenStockExhausted() {
        Coupon c = validCoupon();
        c.setTaken(100); // taken >= stock (100 >= 100)
        when(couponMapper.selectById(1L)).thenReturn(c);

        assertThatThrownBy(() -> couponService.claimCoupon(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已领完");
        verify(userCouponMapper, never()).insert((com.flashsale.orderservice.entity.UserCoupon) any());
    }
}
