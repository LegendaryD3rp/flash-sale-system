package com.flashsale.orderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashsale.orderservice.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    /**
     * 原子领取：taken < stock 时才加1
     */
    @Update("UPDATE coupon SET taken = taken + 1 WHERE id = #{id} AND taken < stock")
    int incrementTaken(@Param("id") Long id);
}
