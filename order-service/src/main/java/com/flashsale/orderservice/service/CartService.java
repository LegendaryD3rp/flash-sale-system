package com.flashsale.orderservice.service;

import com.flashsale.orderservice.vo.CartVO;

import java.util.List;

public interface CartService {

    /**
     * 查询用户购物车列表
     */
    List<CartVO> listCart(Long userId);

    /**
     * 添加商品到购物车（若已存在则增加数量）
     */
    CartVO addCart(Long userId, Long productId, Integer quantity);

    /**
     * 修改购物车项数量
     */
    CartVO updateQuantity(Long userId, Long cartId, Integer quantity);

    /**
     * 删除购物车项
     */
    void deleteCart(Long userId, Long cartId);

    /**
     * 清空用户购物车
     */
    void clearCart(Long userId);
}
