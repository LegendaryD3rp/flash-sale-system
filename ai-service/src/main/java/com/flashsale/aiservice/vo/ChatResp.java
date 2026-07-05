package com.flashsale.aiservice.vo;

/**
 * AI 导购对话响应
 */
public class ChatResp {

    private String reply;

    public ChatResp() {}

    public ChatResp(String reply) {
        this.reply = reply;
    }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
}
