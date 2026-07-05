package com.flashsale.orderservice.controller;

import com.flashsale.common.annotation.AuditLog;
import com.flashsale.common.constant.ErrorCode;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.common.result.Result;
import com.flashsale.orderservice.mapper.OrderMapper;
import com.flashsale.orderservice.mapper.ProductMapper;
import com.flashsale.orderservice.mapper.UserMapper;
import com.flashsale.orderservice.vo.OrderTrendVO;
import com.flashsale.orderservice.vo.StatisticsSummaryVO;
import com.flashsale.orderservice.vo.TopProductVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据统计接口（仅 ADMIN）
 */
@RestController
@RequestMapping("/api/admin/statistics")
public class StatisticsController {

    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;

    public StatisticsController(OrderMapper orderMapper, ProductMapper productMapper,
                                UserMapper userMapper) {
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
    }

    /**
     * GET /api/admin/statistics/summary — 今日概况
     */
    @GetMapping("/summary")
    @AuditLog(module = "数据报表", action = "查询", description = "查看数据概览")
    public Result<StatisticsSummaryVO> summary(@RequestHeader("X-User-Role") String role) {
        checkAdmin(role);
        StatisticsSummaryVO vo = new StatisticsSummaryVO();

        // 今日订单统计
        Map<String, Object> today = orderMapper.selectTodaySummary();
        vo.setTodayOrderCount(today.get("order_count") != null ? ((Number) today.get("order_count")).longValue() : 0);
        vo.setTodaySalesAmount(today.get("sales_amount") != null ? ((Number) today.get("sales_amount")).longValue() : 0);

        // 总用户数
        vo.setTotalUsers(userMapper.countAll());

        // 总商品数
        vo.setTotalProducts(productMapper.countAll());

        // 总订单数
        vo.setTotalOrders(orderMapper.selectCount(null));

        return Result.success(vo);
    }

    /**
     * GET /api/admin/statistics/order-trend?days=7 — 订单趋势
     */
    @GetMapping("/order-trend")
    @AuditLog(module = "数据报表", action = "查询", description = "查看订单趋势")
    public Result<List<OrderTrendVO>> orderTrend(
            @RequestParam(defaultValue = "7") int days,
            @RequestHeader("X-User-Role") String role) {
        checkAdmin(role);
        return Result.success(orderMapper.selectOrderTrend(days));
    }

    /**
     * GET /api/admin/statistics/top-products?limit=10 — 热销商品Top
     */
    @GetMapping("/top-products")
    @AuditLog(module = "数据报表", action = "查询", description = "查看热销商品")
    public Result<List<TopProductVO>> topProducts(
            @RequestParam(defaultValue = "10") int limit,
            @RequestHeader("X-User-Role") String role) {
        checkAdmin(role);
        return Result.success(orderMapper.selectTopProducts(limit));
    }

    private void checkAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作");
        }
    }
}
