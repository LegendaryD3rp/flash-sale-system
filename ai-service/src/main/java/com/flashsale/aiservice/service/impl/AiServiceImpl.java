package com.flashsale.aiservice.service.impl;

import com.flashsale.aiservice.service.AiService;
import com.flashsale.aiservice.vo.ChatReq;
import com.flashsale.aiservice.vo.ChatResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * AI 导购服务 — DeepSeek API 实现
 *
 * 调用 DeepSeek Chat 接口进行智能对话。
 * 支持带历史消息的上下文对话。
 */
@Service
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);

    private final RestTemplate restTemplate;

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.model}")
    private String model;

    @Value("${deepseek.max-tokens}")
    private int maxTokens;

    @Value("${deepseek.temperature}")
    private double temperature;

    public AiServiceImpl() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public ChatResp chat(ChatReq req) {
        String message = req.getMessage() != null ? req.getMessage().trim() : "";
        List<ChatReq.HistoryItem> history = req.getHistory() != null ? req.getHistory() : new ArrayList<>();

        log.debug("AI chat request: {}, history size: {}", message, history.size());

        try {
            String reply = callDeepSeek(message, history);
            return new ChatResp(reply);
        } catch (Exception e) {
            log.error("DeepSeek API call failed", e);
            return new ChatResp("抱歉，我暂时无法回答，请稍后再试。");
        }
    }

    /**
     * 调用 DeepSeek Chat API
     */
    @SuppressWarnings("unchecked")
    private String callDeepSeek(String message, List<ChatReq.HistoryItem> history) {
        String url = baseUrl + "/chat/completions";

        // Build messages array with system prompt + history + current message
        List<Map<String, String>> messages = new ArrayList<>();

        // System prompt
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是FlashSale秒杀电商平台的AI导购助手。你擅长回答商品咨询、秒杀活动规则、订单问题等。用中文回复，简洁热情。");
        messages.add(systemMsg);

        // History
        for (ChatReq.HistoryItem item : history) {
            Map<String, String> histMsg = new HashMap<>();
            histMsg.put("role", item.getRole());
            histMsg.put("content", item.getContent());
            messages.add(histMsg);
        }

        // Current user message
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", message);
        messages.add(userMsg);

        // Build request body
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);

        // Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        // Call API
        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, request, Map.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, String> respMsg = (Map<String, String>) choice.get("message");
                if (respMsg != null && respMsg.get("content") != null) {
                    return respMsg.get("content");
                }
            }
        }

        log.warn("DeepSeek API returned unexpected response: {}", response.getStatusCode());
        return "抱歉，我暂时无法回答，请稍后再试。";
    }
}
