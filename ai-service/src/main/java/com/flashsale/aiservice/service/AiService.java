package com.flashsale.aiservice.service;

import com.flashsale.aiservice.vo.ChatReq;
import com.flashsale.aiservice.vo.ChatResp;

public interface AiService {

    /**
     * AI 对话
     * @param req 用户消息 + 历史记录
     * @return AI 回复
     */
    ChatResp chat(ChatReq req);
}
