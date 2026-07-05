package com.flashsale.orderservice.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashsale.common.constant.ErrorCode;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.orderservice.mapper.OrderMapper;
import com.flashsale.orderservice.service.OrderService;
import com.flashsale.orderservice.vo.OrderVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;

    public OrderServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public IPage<OrderVO> listUserOrders(Long userId, int page, int size) {
        int offset = (page - 1) * size;

        int total = orderMapper.countOrders(userId);
        List<OrderVO> list = orderMapper.selectOrderVOPage(offset, size, userId);

        Page<OrderVO> pg = new Page<>(page, size, total);
        pg.setRecords(list);
        return pg;
    }

    @Override
    public OrderVO getOrderById(Long orderId, Long userId, String role) {
        OrderVO vo = orderMapper.selectOrderVOById(orderId);
        if (vo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        // 归属校验：非管理员只能看自己的订单
        if (!"ADMIN".equals(role) && !vo.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该订单");
        }
        return vo;
    }

    @Override
    public IPage<OrderVO> listAllOrders(int page, int size) {
        int offset = (page - 1) * size;

        int total = orderMapper.countOrders(null);
        List<OrderVO> list = orderMapper.selectOrderVOPage(offset, size, null);

        Page<OrderVO> pg = new Page<>(page, size, total);
        pg.setRecords(list);
        return pg;
    }
}
