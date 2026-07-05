package com.flashsale.userservice.controller;

import com.flashsale.common.result.Result;
import com.flashsale.userservice.service.UserService;
import com.flashsale.userservice.vo.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * POST /api/user/register — 用户注册
     */
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterReq req) {
        Long userId = userService.register(req);
        return Result.success(userId);
    }

    /**
     * POST /api/user/login — 用户登录
     */
    @PostMapping("/login")
    public Result<LoginResp> login(@Valid @RequestBody LoginReq req) {
        LoginResp resp = userService.login(req);
        return Result.success(resp);
    }

    /**
     * GET /api/user/me — 获取当前登录用户信息
     */
    @GetMapping("/me")
    public Result<UserVO> me(@RequestHeader("X-User-Id") Long userId) {
        UserVO vo = userService.getUserById(userId);
        return Result.success(vo);
    }

    /**
     * PUT /api/user/profile — 修改个人资料（昵称、邮箱）
     */
    @PutMapping("/profile")
    public Result<Void> updateProfile(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody ProfileUpdateReq req) {
        userService.updateProfile(userId, req);
        return Result.success();
    }

    /**
     * PUT /api/user/password — 修改密码
     */
    @PutMapping("/password")
    public Result<Void> updatePassword(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody PasswordUpdateReq req) {
        userService.updatePassword(userId, req);
        return Result.success();
    }

    /**
     * POST /api/user/forgot-password — 忘记密码（验证用户名+邮箱，返回重置token）
     */
    @PostMapping("/forgot-password")
    public Result<String> forgotPassword(@RequestBody ForgotPasswordReq req) {
        String token = userService.forgotPassword(req.getUsername(), req.getEmail());
        return Result.success(token);
    }

    /**
     * POST /api/user/reset-password — 重置密码
     */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@RequestBody ResetPasswordReq req) {
        userService.resetPassword(req.getToken(), req.getNewPassword());
        return Result.success();
    }
}
