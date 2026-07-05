package com.flashsale.orderservice.unit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashsale.orderservice.entity.Cart;
import com.flashsale.orderservice.mapper.CartMapper;
import com.flashsale.orderservice.service.impl.CartServiceImpl;
import com.flashsale.orderservice.vo.CartVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartMapper cartMapper;

    @InjectMocks
    private CartServiceImpl cartService;

    @Test
    void addCart_ShouldInsert_WhenNew() {
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(cartMapper.insert(any(Cart.class))).thenReturn(1);

        cartService.addCart(1L, 10L, 2);
        verify(cartMapper).insert(any(Cart.class));
    }

    @Test
    void addCart_ShouldUpdate_WhenExisting() {
        Cart existing = new Cart();
        existing.setId(5L);
        existing.setQuantity(1);
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(cartMapper.updateById(any(Cart.class))).thenReturn(1);

        cartService.addCart(1L, 10L, 2);
        verify(cartMapper).updateById(any(Cart.class));
    }

    @Test
    void listCart_ShouldReturnList() {
        CartVO vo = new CartVO();
        vo.setProductName("测试商品");
        when(cartMapper.selectCartVOList(1L)).thenReturn(List.of(vo));

        List<CartVO> result = cartService.listCart(1L);
        assertThat(result).hasSize(1);
    }

    @Test
    void deleteCart_ShouldCallDeleteById() {
        Cart cart = new Cart();
        cart.setId(100L);
        cart.setUserId(1L);
        when(cartMapper.selectById(100L)).thenReturn(cart);

        cartService.deleteCart(1L, 100L);
        verify(cartMapper).deleteById(100L);
    }
}
