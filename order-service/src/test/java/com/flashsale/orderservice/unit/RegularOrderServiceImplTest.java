package com.flashsale.orderservice.unit;

import com.flashsale.common.exception.BusinessException;
import com.flashsale.orderservice.entity.Order;
import com.flashsale.orderservice.mapper.CartMapper;
import com.flashsale.orderservice.mapper.OrderMapper;
import com.flashsale.orderservice.service.CouponService;
import com.flashsale.orderservice.service.impl.RegularOrderServiceImpl;
import com.flashsale.orderservice.vo.CartVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DeadlockLoserDataAccessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegularOrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private CartMapper cartMapper;
    @Mock
    private com.flashsale.orderservice.mapper.AddressMapper addressMapper;
    @Mock
    private com.flashsale.orderservice.mapper.ProductMapper productMapper;
    @Mock
    private CouponService couponService;

    @InjectMocks
    private RegularOrderServiceImpl regularOrderService;

    // ========== 原有状态流转测试 ==========

    @Test
    void pay_ShouldUpdateOrderStatus() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(1L);
        order.setStatus("PENDING_PAY");
        when(orderMapper.selectById(1L)).thenReturn(order);

        regularOrderService.pay(1L, 1L);
        verify(orderMapper).updateById((Order) any());
    }

    @Test
    void pay_ShouldThrow_WhenNotOwner() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(2L);
        order.setStatus("PENDING_PAY");
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> regularOrderService.pay(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void cancel_ShouldUpdateStatus_WhenOwner() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(1L);
        order.setStatus("PENDING_PAY");
        when(orderMapper.selectById(1L)).thenReturn(order);

        regularOrderService.cancel(1L, 1L);
        verify(orderMapper).updateById((Order) any());
    }

    @Test
    void receive_ShouldUpdateStatus_WhenOwner() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(1L);
        order.setStatus("SHIPPED");
        when(orderMapper.selectById(1L)).thenReturn(order);

        regularOrderService.receive(1L, 1L);
        verify(orderMapper).updateById((Order) any());
    }

    @Test
    void ship_ShouldUpdateStatus() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus("PAID");
        when(orderMapper.selectById(1L)).thenReturn(order);

        regularOrderService.ship(1L);
        verify(orderMapper).updateById((Order) any());
    }

    // ========== P1-⑬ 非法状态流转 ==========

    @Test
    void pay_ShouldThrow_WhenStatusIsPaidAlready() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(1L);
        order.setStatus("PAID");
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> regularOrderService.pay(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可支付");
    }

    @Test
    void cancel_ShouldThrow_WhenStatusIsShipped() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(1L);
        order.setStatus("SHIPPED");
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> regularOrderService.cancel(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可取消");
    }

    @Test
    void receive_ShouldThrow_WhenStatusIsPendingPay() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(1L);
        order.setStatus("PENDING_PAY");
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> regularOrderService.receive(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可确认收货");
    }

    @Test
    void ship_ShouldThrow_WhenStatusIsCanceled() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus("CANCELLED");
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> regularOrderService.ship(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可发货");
    }

    // ========== P0-④ 死锁重试 3 次后成功 ==========

    @Test
    void createFromCart_ShouldSucceed_AfterDeadlockRetries() {
        CartVO cartItem = new CartVO();
        cartItem.setUserId(1L);
        cartItem.setProductId(10L);
        cartItem.setQuantity(1);
        cartItem.setStock(10);
        cartItem.setSubtotal(10000L);
        cartItem.setProductName("测试商品");

        // 前两次抛死锁异常，第三次成功
        when(cartMapper.selectCartVOListByIds(anyList()))
                .thenThrow(new DeadlockLoserDataAccessException("死锁", null))
                .thenThrow(new DeadlockLoserDataAccessException("死锁", null))
                .thenReturn(java.util.Collections.singletonList(cartItem));
        when(productMapper.deductStock(anyLong(), anyInt())).thenReturn(1);
        lenient().when(orderMapper.insert(any(Order.class))).thenReturn(1);
        lenient().when(cartMapper.deleteById(anyLong())).thenReturn(1);

        // 断言最终成功无异常
        regularOrderService.createFromCart(1L, List.of(100L), null, null);
        verify(cartMapper, times(3)).selectCartVOListByIds(anyList());
    }

    // ========== P0-⑤ 死锁重试耗尽后抛出异常 ==========

    @Test
    void createFromCart_ShouldThrow_WhenRetriesExhausted() {
        when(cartMapper.selectCartVOListByIds(anyList()))
                .thenThrow(new DeadlockLoserDataAccessException("死锁", null));

        assertThatThrownBy(() -> regularOrderService.createFromCart(1L, List.of(100L), 1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("繁忙");
    }
}
