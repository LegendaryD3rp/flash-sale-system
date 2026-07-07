package com.flashsale.productservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashsale.common.constant.ErrorCode;
import com.flashsale.common.result.Result;
import com.flashsale.productservice.entity.UserFavorite;
import com.flashsale.productservice.mapper.FavoriteMapper;
import com.flashsale.productservice.vo.FavoriteProductVO;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/product/favorite")
public class FavoriteController {

    private final FavoriteMapper favoriteMapper;

    public FavoriteController(FavoriteMapper favoriteMapper) {
        this.favoriteMapper = favoriteMapper;
    }

    /**
     * POST /api/product/favorite — 收藏商品
     */
    @PostMapping
    public Result<Void> addFavorite(
            @RequestBody Map<String, Long> body,
            @RequestHeader("X-User-Id") Long userId) {
        Long productId = body.get("productId");
        if (productId == null) {
            return Result.error(ErrorCode.BAD_REQUEST, "productId 不能为空");
        }

        // 检查是否已收藏
        LambdaQueryWrapper<UserFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFavorite::getUserId, userId);
        wrapper.eq(UserFavorite::getProductId, productId);
        if (favoriteMapper.selectCount(wrapper) > 0) {
            return Result.success(); // 已收藏，直接返回成功
        }

        UserFavorite fav = new UserFavorite();
        fav.setUserId(userId);
        fav.setProductId(productId);
        favoriteMapper.insert(fav);
        return Result.success();
    }

    /**
     * DELETE /api/product/favorite/{productId} — 取消收藏
     */
    @DeleteMapping("/{productId}")
    public Result<Void> removeFavorite(
            @PathVariable Long productId,
            @RequestHeader("X-User-Id") Long userId) {
        LambdaQueryWrapper<UserFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFavorite::getUserId, userId);
        wrapper.eq(UserFavorite::getProductId, productId);
        favoriteMapper.delete(wrapper);
        return Result.success();
    }

    /**
     * GET /api/product/favorites — 我的收藏列表（分页）
     */
    @GetMapping("/favorites")
    public Result<IPage<FavoriteProductVO>> listFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Id") Long userId) {
        IPage<FavoriteProductVO> result = favoriteMapper.selectFavoriteProducts(new Page<>(page, size), userId);
        return Result.success(result);
    }

    /**
     * GET /api/product/favorite/check?productIds=1,2,3 — 批量检查是否已收藏
     */
    @GetMapping("/check")
    public Result<Map<String, Boolean>> checkFavorites(
            @RequestParam("productIds") String productIds,
            @RequestHeader("X-User-Id") Long userId) {
        Set<Long> ids = Arrays.stream(productIds.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toSet());

        // 查询用户已收藏的商品ID
        LambdaQueryWrapper<UserFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFavorite::getUserId, userId);
        wrapper.in(UserFavorite::getProductId, ids);
        Set<Long> favoritedIds = favoriteMapper.selectList(wrapper)
                .stream()
                .map(UserFavorite::getProductId)
                .collect(Collectors.toSet());

        Map<String, Boolean> result = ids.stream()
                .collect(Collectors.toMap(String::valueOf, favoritedIds::contains));
        return Result.success(result);
    }
}
