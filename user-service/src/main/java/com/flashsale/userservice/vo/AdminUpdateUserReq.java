package com.flashsale.userservice.vo;

/**
 * 管理员修改用户信息的请求体
 */
public class AdminUpdateUserReq {

    private String username;
    private String status;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
