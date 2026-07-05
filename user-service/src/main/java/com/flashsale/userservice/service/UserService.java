package com.flashsale.userservice.service;

import com.flashsale.userservice.vo.*;

public interface UserService {

    /**
     * 用户注册
     * @param req 注册请求
     * @return 用户ID
     */
    Long register(RegisterReq req);

    /**
     * 用户登录
     * @param req 登录请求
     * @return 登录响应（token + userId + role）
     */
    LoginResp login(LoginReq req);

    /**
     * 根据ID获取用户信息
     * @param userId 用户ID
     * @return 用户视图对象
     */
    UserVO getUserById(Long userId);

    /**
     * 修改个人资料（昵称、邮箱）
     * @param userId 用户ID
     * @param req    修改请求
     */
    void updateProfile(Long userId, ProfileUpdateReq req);

    /**
     * 修改密码
     * @param userId 用户ID
     * @param req    密码修改请求（含旧密码校验）
     */
    void updatePassword(Long userId, PasswordUpdateReq req);

    /**
     * 忘记密码 — 验证用户名+邮箱，返回重置token
     * @param username 用户名
     * @param email    邮箱
     * @return 重置token（有效期5分钟）
     */
    String forgotPassword(String username, String email);

    /**
     * 重置密码
     * @param token       重置token
     * @param newPassword 新密码
     */
    void resetPassword(String token, String newPassword);

    // ==================== 管理后台 ====================

    /**
     * 分页查询用户列表（管理员）
     * @param page 页码
     * @param size 每页条数
     * @return 分页结果
     */
    com.baomidou.mybatisplus.core.metadata.IPage<AdminUserVO> adminListUsers(int page, int size);

    /**
     * 获取用户详情（管理员）
     * @param id 用户ID
     * @return 管理员视角的用户视图
     */
    AdminUserVO adminGetUserById(Long id);

    /**
     * 修改用户信息（管理员）
     * @param id  用户ID
     * @param req 修改请求（用户名、状态）
     */
    void adminUpdateUser(Long id, AdminUpdateUserReq req);

    /**
     * 禁用/软删除用户（管理员）
     * @param id 用户ID
     */
    void adminDisableUser(Long id);
}
