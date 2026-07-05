package com.flashsale.userservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.flashsale.common.annotation.AuditLog;
import com.flashsale.common.constant.ErrorCode;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.common.result.Result;
import com.flashsale.userservice.service.UserService;
import com.flashsale.userservice.vo.AdminUserVO;
import com.flashsale.userservice.vo.AdminUpdateUserReq;
import org.springframework.web.bind.annotation.*;

/**
 * 管理后台 — 用户管理
 * 所有接口需要 ADMIN 角色
 */
@RestController
@RequestMapping("/api/admin/user")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /api/admin/user/list?page=1&size=20 — 分页查询用户列表
     */
    @GetMapping("/list")
    @AuditLog(module = "用户管理", action = "查询", description = "查询用户列表")
    public Result<IPage<AdminUserVO>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Role") String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作");
        }
        return Result.success(userService.adminListUsers(page, size));
    }

    /**
     * GET /api/admin/user/{id} — 查看用户详情
     */
    @GetMapping("/{id}")
    @AuditLog(module = "用户管理", action = "查询", description = "查看用户详情 {id}")
    public Result<AdminUserVO> getUserById(
            @PathVariable Long id,
            @RequestHeader("X-User-Role") String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作");
        }
        return Result.success(userService.adminGetUserById(id));
    }

    /**
     * PUT /api/admin/user/{id} — 修改用户信息（用户名、状态）
     */
    @PutMapping("/{id}")
    @AuditLog(module = "用户管理", action = "修改", description = "修改用户 {id}")
    public Result<Void> updateUser(
            @PathVariable Long id,
            @RequestBody AdminUpdateUserReq req,
            @RequestHeader("X-User-Role") String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作");
        }
        userService.adminUpdateUser(id, req);
        return Result.success();
    }

    /**
     * DELETE /api/admin/user/{id} — 软删除/禁用用户
     */
    @DeleteMapping("/{id}")
    @AuditLog(module = "用户管理", action = "删除", description = "禁用用户 {id}")
    public Result<Void> disableUser(
            @PathVariable Long id,
            @RequestHeader("X-User-Role") String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作");
        }
        userService.adminDisableUser(id);
        return Result.success();
    }
}
