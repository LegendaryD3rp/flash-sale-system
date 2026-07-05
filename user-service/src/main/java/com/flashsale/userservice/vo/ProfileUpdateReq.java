package com.flashsale.userservice.vo;

/**
 * 修改个人资料请求
 */
public class ProfileUpdateReq {

    private String nickname;
    private String email;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
