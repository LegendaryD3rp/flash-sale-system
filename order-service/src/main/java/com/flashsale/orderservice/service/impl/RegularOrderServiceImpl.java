package com.flashsale.orderservice.service.impl;

import com.flashsale.common.constant.ErrorCode;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.orderservice.entity.Address;
import com.flashsale.orderservice.entity.Order;
import com.flashsale.orderservice.mapper.AddressMapper;
import com.flashsale.orderservice.mapper.CartMapper;
import com.flashsale.orderservice.mapper.OrderMapper;
import com.flashsale.orderservice.mapper.ProductMapper;
import com.flashsale.orderservice.service.CouponService;
import com.flashsale.orderservice.service.RegularOrderService;
import com.flashsale.orderservice.vo.CartVO;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RegularOrderServiceImpl implements RegularOrderService {

    private static final Logger log = LoggerFactory.getLogger(RegularOrderServiceImpl.class);

    private final OrderMapper orderMapper;
    private final CartMapper cartMapper;
    private final AddressMapper addressMapper;
    private final ProductMapper productMapper;
    private final CouponService couponService;

    public RegularOrderServiceImpl(OrderMapper orderMapper, CartMapper cartMapper,
                                   AddressMapper addressMapper, ProductMapper productMapper,
                                   CouponService couponService) {
        this.orderMapper = orderMapper;
        this.cartMapper = cartMapper;
        this.addressMapper = addressMapper;
        this.productMapper = productMapper;
        this.couponService = couponService;
    }

    @Override
    @Transactional
    public List<Long> createFromCart(Long userId, List<Long> cartIds, Long addressId, Long userCouponId) {
        // 死锁重试：最多3次，指数退避
        DeadlockLoserDataAccessException lastDeadlock = null;
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return doCreateFromCart(userId, cartIds, addressId, userCouponId);
            } catch (DeadlockLoserDataAccessException e) {
                lastDeadlock = e;
                if (attempt < maxRetries) {
                    long backoff = (long) Math.pow(2, attempt) * 50; // 100ms, 200ms, 400ms
                    log.warn("Deadlock detected (attempt {}/{}), retrying after {}ms",
                            attempt, maxRetries, backoff);
                    try { Thread.sleep(backoff); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                "下单服务繁忙，请重试（死锁:" + lastDeadlock.getMessage() + "）");
    }

    /**
     * 实际下单逻辑（包在死锁重试内）
     */
    private List<Long> doCreateFromCart(Long userId, List<Long> cartIds, Long addressId, Long userCouponId) {
        // 查询选中的购物车项（含商品信息）
        List<CartVO> cartItems = cartMapper.selectCartVOListByIds(cartIds);
        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "购物车项不存在");
        }

        // 校验归属和库存
        for (CartVO item : cartItems) {
            if (!item.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该购物车项");
            }
            if (item.getQuantity() > item.getStock()) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "商品「" + item.getProductName() + "」库存不足");
            }
        }

        // 按商品ID排序：防止循环等待导致MySQL死锁
        cartItems.sort(Comparator.comparingLong(CartVO::getProductId));

        // 查询地址信息
        String receiverName = null, receiverPhone = null;
        String deliveryProvince = null, deliveryCity = null, deliveryDistrict = null, deliveryAddress = null;
        if (addressId != null) {
            Address addr = addressMapper.selectById(addressId);
            if (addr == null || !addr.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "地址不存在");
            }
            receiverName = addr.getReceiverName();
            receiverPhone = addr.getReceiverPhone();
            deliveryProvince = addr.getProvince();
            deliveryCity = addr.getCity();
            deliveryDistrict = addr.getDistrict();
            deliveryAddress = addr.getDetailAddress();
        }

        // 优惠券处理：计算总金额和优惠
        Long totalAmount = cartItems.stream().mapToLong(CartVO::getSubtotal).sum();
        Long couponId = null;
        Long discount = 0L;
        Long appliedUserCouponId = null;
        if (userCouponId != null) {
            appliedUserCouponId = userCouponId;
            discount = couponService.calcDiscount(userCouponId, totalAmount);
            couponId = couponService.getCouponIdByUserCouponId(userCouponId);
        }

        LocalDateTime now = LocalDateTime.now();
        List<Long> orderIds = new ArrayList<>();
        long baseId = Math.abs(UUID.randomUUID().getLeastSignificantBits());

        for (int i = 0; i < cartItems.size(); i++) {
            CartVO item = cartItems.get(i);

            // 扣减库存（乐观锁，stock >= quantity 才扣成功）
            int rows = productMapper.deductStock(item.getProductId(), item.getQuantity());
            if (rows == 0) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "商品「" + item.getProductName() + "」库存不足");
            }

            // 分摊优惠：如果是第一个订单且使用了优惠券，则应用折扣
            Long itemDiscount = 0L;
            Long itemCouponId = null;
            Long itemUserCouponId = null;
            if (i == 0 && appliedUserCouponId != null && discount > 0) {
                itemDiscount = discount;
                itemCouponId = couponId;
                itemUserCouponId = appliedUserCouponId;
            }

            Order order = new Order();
            order.setId(baseId + i);
            order.setUserId(userId);
            order.setProductId(item.getProductId());
            order.setQuantity(item.getQuantity());
            order.setTotalAmount(item.getSubtotal());
            order.setDiscount(itemDiscount);
            order.setCouponId(itemCouponId);
            order.setUserCouponId(itemUserCouponId);
            order.setStatus("PENDING_PAY");
            order.setAddressId(addressId);
            order.setReceiverName(receiverName);
            order.setReceiverPhone(receiverPhone);
            order.setDeliveryProvince(deliveryProvince);
            order.setDeliveryCity(deliveryCity);
            order.setDeliveryDistrict(deliveryDistrict);
            order.setDeliveryAddress(deliveryAddress);
            order.setSeckillPrice(0L);
            order.setSeckillActivityId(null);
            order.setCreatedAt(now);
            order.setUpdatedAt(now);
            orderMapper.insert(order);
            orderIds.add(order.getId());
        }

        // 删除已下单的购物车项
        List<Long> idsToDelete = cartItems.stream().map(CartVO::getId).collect(Collectors.toList());
        cartMapper.deleteBatchIds(idsToDelete);

        // 使用优惠券（标记为已用，关联第一个订单）
        if (appliedUserCouponId != null && !orderIds.isEmpty()) {
            couponService.useCoupon(appliedUserCouponId, orderIds.get(0));
        }

        return orderIds;
    }

    @Override
    @Transactional
    public void pay(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该订单");
        }
        if (!"PENDING_PAY".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可支付");
        }
        order.setStatus("PAID");
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Override
    @Transactional
    public void receive(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该订单");
        }
        if (!"SHIPPED".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可确认收货");
        }
        order.setStatus("RECEIVED");
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Override
    @Transactional
    public void cancel(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该订单");
        }
        if (!"PENDING_PAY".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可取消");
        }
        order.setStatus("CANCELLED");
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);

        // 释放优惠券
        if (order.getUserCouponId() != null) {
            couponService.releaseCoupon(order.getUserCouponId());
        }
    }

    @Override
    @Transactional
    public void ship(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!"PAID".equals(order.getStatus()) && !"SUCCESS".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可发货");
        }
        order.setStatus("SHIPPED");
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
    }
}
