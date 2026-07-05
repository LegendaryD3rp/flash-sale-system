package com.flashsale.userservice.vo;

/**
 * 重置密码请求
 */
public class ResetPasswordReq {

    private String token;
    private String newPassword;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
