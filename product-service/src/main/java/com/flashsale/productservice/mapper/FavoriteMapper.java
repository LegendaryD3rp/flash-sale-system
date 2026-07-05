package com.flashsale.productservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashsale.productservice.entity.UserFavorite;
import com.flashsale.productservice.vo.FavoriteProductVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FavoriteMapper extends BaseMapper<UserFavorite> {

    /**
     * 查询用户收藏的商品列表（带商品信息）
     */
    IPage<FavoriteProductVO> selectFavoriteProducts(Page<?> page, @Param("userId") Long userId);
}
