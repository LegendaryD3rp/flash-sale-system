package com.flashsale.orderservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper — order-service 仅用于统计
 */
@Mapper
public interface UserMapper {

    @Select("SELECT COUNT(*) FROM user")
    long countAll();
}
