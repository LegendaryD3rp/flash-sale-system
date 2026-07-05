package com.flashsale.orderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashsale.orderservice.entity.Order;
import com.flashsale.orderservice.vo.OrderTrendVO;
import com.flashsale.orderservice.vo.OrderVO;
import com.flashsale.orderservice.vo.TopProductVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    List<OrderVO> selectOrderVOPage(@Param("offset") int offset,
                                    @Param("size") int size,
                                    @Param("userId") Long userId);

    int countOrders(@Param("userId") Long userId);

    OrderVO selectOrderVOById(@Param("id") Long id);

    /**
     * 今日订单数和销售额
     */
    Map<String, Object> selectTodaySummary();

    /**
     * 近N天订单趋势
     */
    List<OrderTrendVO> selectOrderTrend(@Param("days") int days);

    /**
     * 热销商品TopN
     */
    List<TopProductVO> selectTopProducts(@Param("limit") int limit);
}
