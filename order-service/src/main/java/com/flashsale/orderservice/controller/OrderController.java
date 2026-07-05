package com.flashsale.orderservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.flashsale.common.constant.ErrorCode;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.common.result.Result;
import com.flashsale.orderservice.service.OrderService;
import com.flashsale.orderservice.service.RegularOrderService;
import com.flashsale.orderservice.vo.OrderVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class OrderController {

    private final OrderService orderService;
    private final RegularOrderService regularOrderService;

    public OrderController(OrderService orderService, RegularOrderService regularOrderService) {
        this.orderService = orderService;
        this.regularOrderService = regularOrderService;
    }

    /**
     * GET /api/order/list — 当前用户订单列表
     */
    @GetMapping("/api/order/list")
    public Result<IPage<OrderVO>> listMyOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(orderService.listUserOrders(userId, page, size));
    }

    /**
     * GET /api/order/{orderId} — 订单详情（带归属校验）
     */
    @GetMapping("/api/order/{orderId}")
    public Result<OrderVO> getOrder(@PathVariable Long orderId,
                                    @RequestHeader("X-User-Id") Long userId,
                                    @RequestHeader("X-User-Role") String role) {
        return Result.success(orderService.getOrderById(orderId, userId, role));
    }

    /**
     * GET /api/admin/order/list — 管理员查看所有订单
     */
    @GetMapping("/api/admin/order/list")
    public Result<IPage<OrderVO>> listAllOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Role") String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作");
        }
        return Result.success(orderService.listAllOrders(page, size));
    }

    // ==================== 常规下单 ====================

    /**
     * POST /api/order/create — 从购物车创建订单
     * 请求体: { "cartIds": [1,2,3], "addressId": 1 }
     */
    @PostMapping("/api/order/create")
    public Result<List<Long>> createOrder(@RequestHeader("X-User-Id") Long userId,
                                          @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> cartIdsRaw = (List<Integer>) body.get("cartIds");
        if (cartIdsRaw == null || cartIdsRaw.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要结算的商品");
        }
        List<Long> cartIds = cartIdsRaw.stream().map(Long::valueOf).toList();
        Long addressId = body.containsKey("addressId") && body.get("addressId") != null
                ? Long.valueOf(body.get("addressId").toString())
                : null;
        Long userCouponId = body.containsKey("userCouponId") && body.get("userCouponId") != null
                ? Long.valueOf(body.get("userCouponId").toString())
                : null;
        return Result.success(regularOrderService.createFromCart(userId, cartIds, addressId, userCouponId));
    }

    // ==================== 支付 / 状态流转 ====================

    /**
     * POST /api/order/{id}/pay — 模拟支付
     */
    @PostMapping("/api/order/{id}/pay")
    public Result<Void> payOrder(@PathVariable Long id,
                                 @RequestHeader("X-User-Id") Long userId) {
        regularOrderService.pay(userId, id);
        return Result.success();
    }

    /**
     * POST /api/order/{id}/receive — 确认收货
     */
    @PostMapping("/api/order/{id}/receive")
    public Result<Void> receiveOrder(@PathVariable Long id,
                                     @RequestHeader("X-User-Id") Long userId) {
        regularOrderService.receive(userId, id);
        return Result.success();
    }

    /**
     * POST /api/order/{id}/cancel — 取消订单
     */
    @PostMapping("/api/order/{id}/cancel")
    public Result<Void> cancelOrder(@PathVariable Long id,
                                    @RequestHeader("X-User-Id") Long userId) {
        regularOrderService.cancel(userId, id);
        return Result.success();
    }

    // ==================== 管理发货 ====================

    /**
     * POST /api/admin/order/{id}/ship — 管理员发货
     */
    @PostMapping("/api/admin/order/{id}/ship")
    public Result<Void> shipOrder(@PathVariable Long id,
                                  @RequestHeader("X-User-Role") String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作");
        }
        regularOrderService.ship(id);
        return Result.success();
    }
}
