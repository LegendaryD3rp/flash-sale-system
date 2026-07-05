package com.flashsale.orderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashsale.orderservice.entity.Cart;
import com.flashsale.orderservice.vo.CartVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CartMapper extends BaseMapper<Cart> {

    List<CartVO> selectCartVOList(@Param("userId") Long userId);

    List<CartVO> selectCartVOListByIds(@Param("ids") List<Long> ids);
}
