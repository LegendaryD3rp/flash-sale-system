package com.flashsale.userservice.vo;

/**
 * 忘记密码请求
 */
public class ForgotPasswordReq {

    private String username;
    private String email;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
