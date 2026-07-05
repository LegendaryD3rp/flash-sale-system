package com.flashsale.orderservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashsale.common.constant.ErrorCode;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.orderservice.entity.Cart;
import com.flashsale.orderservice.mapper.CartMapper;
import com.flashsale.orderservice.service.CartService;
import com.flashsale.orderservice.vo.CartVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CartServiceImpl implements CartService {

    private final CartMapper cartMapper;

    public CartServiceImpl(CartMapper cartMapper) {
        this.cartMapper = cartMapper;
    }

    @Override
    public List<CartVO> listCart(Long userId) {
        return cartMapper.selectCartVOList(userId);
    }

    @Override
    @Transactional
    public CartVO addCart(Long userId, Long productId, Integer quantity) {
        if (quantity == null || quantity < 1) {
            quantity = 1;
        }

        // 检查是否已存在该商品
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getProductId, productId);
        Cart existing = cartMapper.selectOne(wrapper);

        if (existing != null) {
            // 已存在则增加数量
            existing.setQuantity(existing.getQuantity() + quantity);
            cartMapper.updateById(existing);
        } else {
            // 不存在则新增
            existing = new Cart();
            existing.setUserId(userId);
            existing.setProductId(productId);
            existing.setQuantity(quantity);
            cartMapper.insert(existing);
        }

        // 返回更新后的购物车列表（只返回刚操作的这条的VO）
        List<CartVO> list = cartMapper.selectCartVOList(userId);
        return list.stream()
                .filter(v -> v.getProductId().equals(productId))
                .findFirst()
                .orElse(null);
    }

    @Override
    @Transactional
    public CartVO updateQuantity(Long userId, Long cartId, Integer quantity) {
        Cart cart = cartMapper.selectById(cartId);
        if (cart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "购物车项不存在");
        }
        if (!cart.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改该购物车项");
        }
        if (quantity == null || quantity < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "数量不合法");
        }
        if (quantity == 0) {
            // 数量为0则删除
            cartMapper.deleteById(cartId);
            return null;
        }
        cart.setQuantity(quantity);
        cartMapper.updateById(cart);

        List<CartVO> list = cartMapper.selectCartVOList(userId);
        return list.stream()
                .filter(v -> v.getId().equals(cartId))
                .findFirst()
                .orElse(null);
    }

    @Override
    @Transactional
    public void deleteCart(Long userId, Long cartId) {
        Cart cart = cartMapper.selectById(cartId);
        if (cart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "购物车项不存在");
        }
        if (!cart.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权删除该购物车项");
        }
        cartMapper.deleteById(cartId);
    }

    @Override
    @Transactional
    public void clearCart(Long userId) {
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId);
        cartMapper.delete(wrapper);
    }
}
