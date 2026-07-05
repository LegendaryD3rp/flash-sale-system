package com.flashsale.aiservice.controller;

import com.flashsale.aiservice.service.AiService;
import com.flashsale.aiservice.vo.ChatReq;
import com.flashsale.aiservice.vo.ChatResp;
import com.flashsale.common.result.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * POST /api/ai/chat — AI 导购对话
     */
    @PostMapping("/chat")
    public Result<ChatResp> chat(@RequestBody ChatReq req) {
        ChatResp resp = aiService.chat(req);
        return Result.success(resp);
    }
}
