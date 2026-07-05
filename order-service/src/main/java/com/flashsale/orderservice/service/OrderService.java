package com.flashsale.orderservice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.flashsale.orderservice.vo.OrderVO;

public interface OrderService {

    /**
     * 查询用户订单列表（按 userId，分页，按时间倒序）
     */
    IPage<OrderVO> listUserOrders(Long userId, int page, int size);

    /**
     * 查询订单详情（按 orderId），带归属校验
     */
    OrderVO getOrderById(Long orderId, Long userId, String role);

    /**
     * 管理员查询所有订单
     */
    IPage<OrderVO> listAllOrders(int page, int size);
}
