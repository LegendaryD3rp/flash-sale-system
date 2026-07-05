package com.flashsale.productservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.flashsale.common.annotation.AuditLog;
import com.flashsale.common.constant.ErrorCode;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.common.result.Result;
import com.flashsale.productservice.service.ProductService;
import com.flashsale.productservice.vo.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/product")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * GET /api/product — 分页查询商品列表
     */
    @GetMapping
    public Result<IPage<ProductVO>> listProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestHeader(value = "X-User-Role", defaultValue = "CUSTOMER") String role) {

        IPage<ProductVO> result = productService.listProducts(page, size, keyword, category,
                minPrice, maxPrice, role);
        return Result.success(result);
    }

    /**
     * GET /api/product/search — 搜索商品（名称 + 描述模糊匹配）
     */
    @GetMapping("/search")
    public Result<IPage<ProductVO>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Result.error(ErrorCode.BAD_REQUEST, "搜索关键词不能为空");
        }
        IPage<ProductVO> result = productService.search(page, size, keyword.trim());
        return Result.success(result);
    }

    /**
     * GET /api/product/{id} — 查询商品详情
     */
    @GetMapping("/{id}")
    public Result<ProductVO> getProduct(@PathVariable Long id) {
        ProductVO vo = productService.getProductById(id);
        return Result.success(vo);
    }

    /**
     * POST /api/product — 新增商品（仅 ADMIN）
     */
    @PostMapping
    @AuditLog(module = "商品管理", action = "新增", description = "新增商品")
    public Result<Long> createProduct(@Valid @RequestBody ProductCreateReq req,
                                      @RequestHeader(value = "X-User-Role", defaultValue = "CUSTOMER") String role) {
        checkAdmin(role);
        Long id = productService.createProduct(req);
        return Result.success(id);
    }

    /**
     * PUT /api/product/{id} — 编辑商品（仅 ADMIN）
     */
    @PutMapping("/{id}")
    @AuditLog(module = "商品管理", action = "修改", description = "修改商品 {id}")
    public Result<Long> updateProduct(@PathVariable Long id,
                                      @RequestBody ProductUpdateReq req,
                                      @RequestHeader(value = "X-User-Role", defaultValue = "CUSTOMER") String role) {
        checkAdmin(role);
        Long productId = productService.updateProduct(id, req);
        return Result.success(productId);
    }

    /**
     * DELETE /api/product/{id} — 下架商品（仅 ADMIN）
     */
    @DeleteMapping("/{id}")
    @AuditLog(module = "商品管理", action = "删除", description = "下架商品 {id}")
    public Result<Long> deleteProduct(@PathVariable Long id,
                                      @RequestHeader(value = "X-User-Role", defaultValue = "CUSTOMER") String role) {
        checkAdmin(role);
        Long productId = productService.deleteProduct(id);
        return Result.success(productId);
    }

    private void checkAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作");
        }
    }
}
