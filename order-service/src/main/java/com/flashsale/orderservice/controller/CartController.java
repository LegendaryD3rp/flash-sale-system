package com.flashsale.orderservice.controller;

import com.flashsale.common.result.Result;
import com.flashsale.orderservice.mapper.CartMapper;
import com.flashsale.orderservice.service.CartService;
import com.flashsale.orderservice.vo.CartVO;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;
    private final CartMapper cartMapper;

    public CartController(CartService cartService, CartMapper cartMapper) {
        this.cartService = cartService;
        this.cartMapper = cartMapper;
    }

    /**
     * GET /api/cart/listByIds — 按 ID 列表查询购物车项（校验归属）
     */
    @GetMapping("/listByIds")
    public Result<List<CartVO>> listCartByIds(@RequestParam String ids,
                                               @RequestHeader("X-User-Id") Long userId) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .map(Long::valueOf)
                .collect(Collectors.toList());
        List<CartVO> cartItems = cartMapper.selectCartVOListByIds(idList);
        // 只返回当前用户的购物车项
        List<CartVO> filtered = cartItems.stream()
                .filter(item -> item.getUserId().equals(userId))
                .collect(Collectors.toList());
        return Result.success(filtered);
    }

    /**
     * GET /api/cart/list — 查询用户购物车
     */
    @GetMapping("/list")
    public Result<List<CartVO>> listCart(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(cartService.listCart(userId));
    }

    /**
     * POST /api/cart/add — 添加商品到购物车
     * 请求体: { "productId": 1, "quantity": 2 }
     */
    @PostMapping("/add")
    public Result<CartVO> addCart(@RequestHeader("X-User-Id") Long userId,
                                  @RequestBody Map<String, Object> body) {
        Long productId = Long.valueOf(body.get("productId").toString());
        Integer quantity = body.containsKey("quantity")
                ? Integer.valueOf(body.get("quantity").toString())
                : 1;
        return Result.success(cartService.addCart(userId, productId, quantity));
    }

    /**
     * PUT /api/cart/{id} — 修改数量
     * 请求体: { "quantity": 3 }
     */
    @PutMapping("/{id}")
    public Result<CartVO> updateQuantity(@PathVariable Long id,
                                         @RequestHeader("X-User-Id") Long userId,
                                         @RequestBody Map<String, Object> body) {
        Integer quantity = Integer.valueOf(body.get("quantity").toString());
        return Result.success(cartService.updateQuantity(userId, id, quantity));
    }

    /**
     * DELETE /api/cart/{id} — 删除一项
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteCart(@PathVariable Long id,
                                   @RequestHeader("X-User-Id") Long userId) {
        cartService.deleteCart(userId, id);
        return Result.success();
    }

    /**
     * POST /api/cart/clear — 清空购物车
     */
    @PostMapping("/clear")
    public Result<Void> clearCart(@RequestHeader("X-User-Id") Long userId) {
        cartService.clearCart(userId);
        return Result.success();
    }
}
