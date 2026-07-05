package com.flashsale.orderservice.unit;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.orderservice.mapper.OrderMapper;
import com.flashsale.orderservice.service.impl.OrderServiceImpl;
import com.flashsale.orderservice.vo.OrderVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void listUserOrders_ShouldReturnPage() {
        OrderVO vo = new OrderVO();
        vo.setId(1L);
        vo.setTotalAmount(10000L);

        when(orderMapper.countOrders(1L)).thenReturn(1);
        when(orderMapper.selectOrderVOPage(anyInt(), anyInt(), anyLong())).thenReturn(List.of(vo));

        IPage<OrderVO> result = orderService.listUserOrders(1L, 1, 10);
        assertThat(result.getRecords()).isNotEmpty();
    }

    @Test
    void getOrderById_ShouldReturn_WhenOwner() {
        OrderVO vo = new OrderVO();
        vo.setId(100L);
        vo.setUserId(1L);

        when(orderMapper.selectOrderVOById(100L)).thenReturn(vo);

        OrderVO result = orderService.getOrderById(100L, 1L, "CUSTOMER");
        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    void getOrderById_ShouldThrow_WhenNotOwner() {
        OrderVO vo = new OrderVO();
        vo.setId(100L);
        vo.setUserId(2L);

        when(orderMapper.selectOrderVOById(100L)).thenReturn(vo);

        assertThatThrownBy(() -> orderService.getOrderById(100L, 1L, "CUSTOMER"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void listAllOrders_ShouldReturn() {
        OrderVO vo = new OrderVO();
        vo.setId(1L);

        when(orderMapper.countOrders(nullable(Long.class))).thenReturn(3);
        when(orderMapper.selectOrderVOPage(anyInt(), anyInt(), nullable(Long.class)))
                .thenReturn(List.of(vo, vo, vo));

        IPage<OrderVO> result = orderService.listAllOrders(1, 10);
        assertThat(result.getTotal()).isEqualTo(3);
    }
}
