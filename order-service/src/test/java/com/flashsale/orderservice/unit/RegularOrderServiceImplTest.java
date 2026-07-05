package com.flashsale.orderservice.unit;

import com.flashsale.common.exception.BusinessException;
import com.flashsale.orderservice.entity.Order;
import com.flashsale.orderservice.mapper.CartMapper;
import com.flashsale.orderservice.mapper.OrderMapper;
import com.flashsale.orderservice.service.CouponService;
import com.flashsale.orderservice.service.impl.RegularOrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
}
