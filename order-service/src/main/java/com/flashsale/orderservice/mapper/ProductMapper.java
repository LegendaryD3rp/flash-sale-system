package com.flashsale.orderservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 商品 Mapper — order-service 直接操作 product 表用于库存扣减和统计
 */
@Mapper
public interface ProductMapper {

    /**
     * 扣减库存（乐观锁：stock >= quantity 时才扣减）
     * @return 影响行数，0 表示库存不足
     */
    @Update("UPDATE product SET stock = stock - #{quantity} WHERE id = #{productId} AND stock >= #{quantity}")
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 商品总数
     */
    @Select("SELECT COUNT(*) FROM product")
    long countAll();
}
