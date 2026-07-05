package com.flashsale.orderservice.service;

import com.flashsale.orderservice.vo.OrderVO;
import java.util.List;

public interface RegularOrderService {

    /**
     * 从购物车创建订单
     * @param userId 用户ID
     * @param cartIds 选中的购物车项ID列表
     * @param addressId 收货地址ID
     * @param userCouponId 使用的优惠券ID（可选，可为null）
     * @return 创建的订单ID列表
     */
    List<Long> createFromCart(Long userId, List<Long> cartIds, Long addressId, Long userCouponId);

    /**
     * 支付订单
     */
    void pay(Long userId, Long orderId);

    /**
     * 确认收货
     */
    void receive(Long userId, Long orderId);

    /**
     * 取消订单
     */
    void cancel(Long userId, Long orderId);

    /**
     * 管理员发货
     */
    void ship(Long orderId);
}
